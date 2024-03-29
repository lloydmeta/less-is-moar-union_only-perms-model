## Subtraction via addition in an additive only perms model

An exploration in how to express "allow all except for _these_" perms when the authorisation
backend is the ES Security model, which is [unions-only](https://www.elastic.co/guide/en/elasticsearch/reference/current/authorization.html).

This "subtracting-via-adding" could be applied in other similarly-constrained-and-not-uncommon
backends, but this repo holds a demo that only interfaces with ES and its Security model.

### Basic premise

* If we make the result of each action permission check for an actor and object a vector (magnitude with direction): that is: 0, 1 or -1, we can “subtract by adding”.
* If we only have booleans (1 or 0), assuming that there is a `can_do` function that returns `0` if the "current" actor does _not_ have permission to perform action<sub>0</sub> on the input object, and `1` otherwise, and it can also do so for an "inverse" action, let's say action<sub>0</sub><sup>-1</sup> if the input actor has been explicitly _disallowed_ from doing action<sub>0</sub> on the input object.

```math
\begin{align*}

{authorised\_total_{actor}}(action_{0..n}, object) &= \sum_{a=0}^{n}(can\_do_{actor}(action_0, object)\text{ } + \\&\text{ }(-1 \cdot can\_do_{actor}(action_0^{-1}, object)))\\\\


{authorised}_{actor}(action_{0..n}, object) &= \begin{cases}
    1 & \text{if } {authorised\_total_{actor}}(action_{0..n}, object) = n \\
    0 & \text{otherwise.}
\end{cases}
\end{align*}
```

<sub>There is probably a way to express the above in terms of multiplication, but addition <em>feels</em> more straightforward a mapping..</sub>

The `isAuthorised` function in `poc.sc` demonstrates an implementation of the above building on
* [Users](https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-put-user.html)
* [AppPrivileges](https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-put-privileges.html)
* [Roles](https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-put-role.html)
* [_has_privileges](https://www.elastic.co/guide/en/elasticsearch/reference/current/security-api-has-privileges.html) call.

## Running the PoC

### Requires

* [Ammonite](https://ammonite.io): must at least support Scala 3 because we're on that no-parens hotness.
* [Docker CLI](https://www.docker.com/products/cli/): [Rancher Desktop](https://rancherdesktop.io) will do

### Running

1. `make start-es` to bring up ES, wait until it's up before proceeding.

   `✅ Elasticsearch security features have been automatically configured!` should show up
2. `make repl` in a separate terminal to load up a REPL with the PoC functions loaded in and tested.
3. Test it out
   1. Assert that the Support user has blanket permissions to `deployment:edit` a given deployment

      ```scala
      val deplIdToTest = generateRandStr()
      isAuthorised(supportEsUser, deplIdToTest -> "deployment:edit")
      // res1: Boolean = true
      ```
   2. Subtract the deployment from the user's allowed set by adding it to the _inverse_ support role

      ```scala
      putEsRole(
        inverse(supportAppPrivName),
        Seq(deplIdToTest)
      )()
      ```
   3. Test the access to that deployment again:

      ```scala
      isAuthorised(supportEsUser, deplIdToTest -> "deployment:edit")
      // res3: Boolean = false
      ```
   4. Remove it by emptying the inverse support resource list

      ```scala
      putEsRole(
        inverse(supportAppPrivName),
        Seq.empty
      )()
      ```
   5. Test the access to that deployment again:

      ```scala
      isAuthorised(supportEsUser, deplIdToTest -> "deployment:edit")
      // res3: Boolean = true
      ```
4. Exit the REPL with `ctrl+d` then `ctrl+c`
5. `make stop-es` to stop and cleanup ES