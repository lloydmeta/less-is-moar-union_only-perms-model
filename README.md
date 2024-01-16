## Subtraction via addition in an additive only perms model

An exploration in how to express "allow all except for _these_" perms when the authorisation
backend is the ES Security model, which is [unions-only](https://www.elastic.co/guide/en/elasticsearch/reference/current/authorization.html).

This "subtracting-via-adding" could be applied in other similarly-constrained-and-not-uncommon
backends, but this repo holds a demo that only interfaces with ES and its Security model.

### Basic premise

* If we make the result of each action permission check for an actor and object a vector (magnitude with direction): that is: 0, 1 or -1, we can “subtract by adding”.
* That is, assuming that if there is a `can_do0` function that checks permissions for action `0` that returns `0` if the input actor does _not_ have permission to perform action `0` on the input object, and `1` otherwise, there is _also_ a function that checks for the inverse, let's say `can_do0_inv` that returns `1` if the input actor has been explicitly _disallowed_ from doing action `0` on the input object.

```math
\begin{gather*}
{authorised\_total}_{0..n}(object) = \sum_{a=0}^{n}can\_do_n(object) + ({-1} \cdot(can\_do_{n^{-1}}(object)) \\

{authorised}_{0..n}(object) = \begin{cases}
    1 & \text{if } {authorised\_total}_{0..n}(object) = n \\
     0 & \text{otherwise.}
\end{cases}
\end{gather*}
```

<sub>There is probably a way to express the above in terms of multiplication, but addition <em>feels</em> more straightforward a mapping..</sub>

The `isAuthorised` function in `poc.sc` demonstrates an implementation of the above.

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