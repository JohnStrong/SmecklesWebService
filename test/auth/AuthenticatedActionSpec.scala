package auth

import com.auth0.jwk.{Jwk, JwkProvider}
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.Results
import play.api.test.*
import play.api.test.Helpers.*

import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.time.Instant
import java.util.Date
import scala.concurrent.{ExecutionContext, Future}
import scala.util.chaining.*

/**
 * Unit tests for [[AuthenticatedAction]].
 *
 * HOW THE MOCKING WORKS:
 *
 * In production, AuthenticatedAction calls Google's JWKS endpoint to fetch public keys
 * for verifying Firebase tokens. We don't want tests hitting the internet, so we:
 *
 *  1. Generate a LOCAL RSA key pair (public + private) at test startup
 *  2. Create a MOCK JwkProvider that returns our local public key when asked for key ID "test-kid"
 *  3. Sign test JWTs with our local private key (mimicking what Google would do)
 *  4. When AuthenticatedAction verifies the token, it asks the mock provider for the key,
 *     gets our local public key, and the signature check passes — all without any network call.
 *
 * To test rejection cases, we either:
 *  - Omit/malform the Authorization header (fails before key lookup)
 *  - Sign with correct key but wrong audience/issuer/expiry (signature passes, claims check fails)
 *  - Use a garbled token string (fails at decode stage)
 */
class AuthenticatedActionSpec extends AnyWordSpec with Matchers {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val projectId = "smeckles-app-11ca3"

  // Step 1: Generate a test RSA key pair — these never leave this test class
  private val keyPair = KeyPairGenerator.getInstance("RSA").tap(_.initialize(2048)).generateKeyPair()
  private val publicKey = keyPair.getPublic.asInstanceOf[RSAPublicKey]
  private val privateKey = keyPair.getPrivate.asInstanceOf[RSAPrivateKey]

  // Step 2: Mock JwkProvider — returns our local public key instead of calling Google
  private def mockJwkProvider(): JwkProvider = {
    val mockJwk = mock(classOf[Jwk])
    when(mockJwk.getPublicKey).thenReturn(publicKey)

    val provider = mock(classOf[JwkProvider])
    when(provider.get("test-kid")).thenReturn(mockJwk)
    provider
  }

  // Step 3: Create an AuthenticatedAction wired with our mock (no Google, no network)
  private def createAction(provider: JwkProvider = mockJwkProvider()): AuthenticatedAction = {
    val config = Configuration("auth.firebase.projectId" -> projectId)
    val factory = new JwkProviderFactory { def create(): JwkProvider = provider }
    new AuthenticatedAction(null.asInstanceOf[play.api.mvc.BodyParsers.Default], config, factory)
  }

  // Helper: sign a valid token with our local private key (mimics Firebase issuing a token)
  private def validToken(
    email: String = "user@test.com",
    uid: String = "uid-123"
  ): String =
    JWT.create()
      .withIssuer(s"https://securetoken.google.com/$projectId")
      .withAudience(projectId)
      .withSubject(uid)
      .withClaim("email", email)
      .withKeyId("test-kid")
      .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
      .sign(Algorithm.RSA256(publicKey, privateKey))

  private def tokenWithWrongAudience(): String =
    JWT.create()
      .withIssuer(s"https://securetoken.google.com/$projectId")
      .withAudience("wrong-project")
      .withSubject("uid-123")
      .withClaim("email", "user@test.com")
      .withKeyId("test-kid")
      .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
      .sign(Algorithm.RSA256(publicKey, privateKey))

  private def expiredToken(): String =
    JWT.create()
      .withIssuer(s"https://securetoken.google.com/$projectId")
      .withAudience(projectId)
      .withSubject("uid-123")
      .withClaim("email", "user@test.com")
      .withKeyId("test-kid")
      .withExpiresAt(Date.from(Instant.now().minusSeconds(3600)))
      .sign(Algorithm.RSA256(publicKey, privateKey))

  "AuthenticatedAction" should {

    "return 401 when Authorization header is missing" in {
      val action = createAction()
      val result = action.async(_ => ???).apply(FakeRequest())

      status(result) shouldBe UNAUTHORIZED
      (contentAsJson(result) \ "error").as[String] should include("Missing")
    }

    "return 401 when Authorization header does not start with Bearer" in {
      val action = createAction()
      val request = FakeRequest().withHeaders("Authorization" -> "Basic abc123")
      val result = action.async(_ => ???).apply(request)

      status(result) shouldBe UNAUTHORIZED
    }

    "return 401 when token is malformed" in {
      val action = createAction()
      val request = FakeRequest().withHeaders("Authorization" -> "Bearer not.a.jwt")
      val result = action.async(_ => ???).apply(request)

      status(result) shouldBe UNAUTHORIZED
      (contentAsJson(result) \ "error").as[String] should include("Invalid token")
    }

    "return 401 when token has wrong audience" in {
      val action = createAction()
      val request = FakeRequest().withHeaders("Authorization" -> s"Bearer ${tokenWithWrongAudience()}")
      val result = action.async(_ => ???).apply(request)

      status(result) shouldBe UNAUTHORIZED
    }

    "return 401 when token is expired" in {
      val action = createAction()
      val request = FakeRequest().withHeaders("Authorization" -> s"Bearer ${expiredToken()}")
      val result = action.async(_ => ???).apply(request)

      status(result) shouldBe UNAUTHORIZED
    }

    "pass through with userId and email when token is valid" in {
      val action = createAction()
      val token = validToken(email = "hello@example.com", uid = "firebase-uid-456")
      val request = FakeRequest().withHeaders("Authorization" -> s"Bearer $token")

      val result = action.async { req =>
        Future.successful(Results.Ok(Json.obj("userId" -> req.userId, "email" -> req.email)))
      }.apply(request)

      status(result) shouldBe OK
      (contentAsJson(result) \ "userId").as[String] shouldBe "firebase-uid-456"
      (contentAsJson(result) \ "email").as[String] shouldBe "hello@example.com"
    }
  }
}
