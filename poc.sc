import $ivy.`com.lihaoyi::requests:0.8.0`
import $ivy.`com.lihaoyi::ujson:3.1.4`

import requests.Session

println("⏳ Loading ...")

// "root" client setup
val baseUrl = "https://localhost:9200"
val rootEsClient = buildEsClient("elastic", System.getenv("ES_PASSWORD"))

// Set up ES Application Privileges and Roles (with their inverses)
val appName = "ecpc"
val supportAppPrivName = "support"
val editDeplAction = "deployment:edit"
val deleteDeplAction = "deployment:delete"
val supportAppPriv =
  AppPriv(supportAppPrivName, Seq(editDeplAction, deleteDeplAction))
createEsAppPrivWithInverse(supportAppPriv)
createRoleWithInverse(supportAppPriv, Seq("*"))

// Set up a Support user that has the "support" Role (and inverse)
val supportEsUser = putEsUser("jacknick", Seq(supportAppPriv.name))

runTests()

println("🎉 Ready.")

final case class AppPriv(name: String, actions: Seq[String])

final case class EsUser(name: String, client: Session)

def generateRandStr(): String = scala.util.Random.alphanumeric.take(36).mkString

def buildEsClient(username: String, password: String): Session =
  requests.Session(
    auth = requests.RequestAuth.Basic(username, password),
    headers = Map("Content-Type" -> "application/json"),
    verifySslCerts = false
  )

def putEsUser(username: String, roles: Seq[String]): EsUser =
  val password = generateRandStr()
  rootEsClient.put(
    url = s"$baseUrl/_security/user/$username",
    data = ujson.Obj(
      "password" -> password,
      "roles" -> roles.flatMap(r => Seq(r, inverse(r)))
    )
  )
  EsUser(username, buildEsClient(username, password))

def inverse(str: String): String = s"anti.${str}"

def createEsAppPrivWithInverse(appPriv: AppPriv): Unit =
  rootEsClient.put(
    url = s"$baseUrl/_security/privilege",
    data = ujson.Obj(
      appName -> ujson.Obj(
        appPriv.name -> ujson.Obj(
          "actions" -> appPriv.actions
        )
      )
    )
  )
  rootEsClient.put(
    url = s"$baseUrl/_security/privilege",
    data = ujson.Obj(
      appName -> ujson.Obj(
        inverse(appPriv.name) -> ujson.Obj(
          "actions" -> appPriv.actions.map: a =>
            inverse(a)
        )
      )
    )
  )

def createRoleWithInverse(appPriv: AppPriv, resources: Seq[String]): Unit =
  putEsRole(appPriv.name, resources)()
  putEsRole(
    inverse(appPriv.name),
    Seq.empty
  )()

def putEsRole(appPrivName: String, resources: Seq[String])(
    roleName: String = appPrivName // in this demo, it's the same
): Unit =
  val resourcesToSend =
    if (resources.isEmpty)
      Seq("_ignore_" /* app Privs need at least one resource */ )
    else
      resources
  rootEsClient.put(
    url = s"$baseUrl/_security/role/${roleName}",
    data = ujson.Obj(
      "applications" -> ujson.Arr(
        ujson.Obj(
          "application" -> appName,
          "privileges" -> ujson.Arr(roleName),
          "resources" -> resourcesToSend
        )
      )
    )
  )

def isAuthorised(esUser: EsUser, objectIdToActions: (String, String)*) =
  def canDo(
      hasPrivsRespBody: ujson.Value
  )(objId: String, action: String): Boolean =
    val objAuthzChecks = hasPrivsRespBody("application")(appName)(objId)
    !objAuthzChecks(inverse(action)).bool && objAuthzChecks(action).bool
  val hasPrivsRequestBody = ujson.Obj(
    "application" -> objectIdToActions.map: (obj, action) =>
      ujson.Obj(
        "application" -> appName,
        "privileges" -> Seq(action, inverse(action)),
        "resources" -> ujson.Arr(obj)
      )
  )
  val hasPrivsResp = esUser.client.post(
    url = s"$baseUrl/_security/user/_has_privileges",
    data = hasPrivsRequestBody
  )
  val hasPrivsRespBody = ujson.read(hasPrivsResp.text)
  objectIdToActions.forall(canDo(hasPrivsRespBody))

def runTests(): Unit =
  println("🧪 Running tests...")
  val deplId = generateRandStr()
  // Check that the user has the expected right to edit any given deployment
  assert(isAuthorised(supportEsUser, deplId -> editDeplAction))

  // Add the deployment to the *inverted* Role
  putEsRole(
    inverse(supportAppPrivName),
    Seq(deplId)
  )()

  // Check that the user can no longer access that particular deployment
  assert(!isAuthorised(supportEsUser, deplId -> editDeplAction))

  // Empty the resources in the *inverted* Role
  putEsRole(
    inverse(supportAppPrivName),
    Seq.empty
  )()

  // Check that the user can again access that particular deployment
  assert(isAuthorised(supportEsUser, deplId -> editDeplAction))

  println("✅ Tests passed.")
