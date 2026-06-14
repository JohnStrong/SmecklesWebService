package api

import auth.JwkProviderFactory
import com.auth0.jwk.{Jwk, JwkProvider}
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mockito.Mockito.*
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder

import java.security.KeyPairGenerator
import java.security.interfaces.{RSAPrivateKey, RSAPublicKey}
import java.time.Instant
import java.util.Date
import scala.util.chaining.*

/**
 * Base trait for functional tests that need authenticated requests.
 *
 * Overrides the Guice binding for [[JwkProviderFactory]] with a test implementation
 * that returns a locally-generated RSA key. No network calls to Google — test JWTs
 * are signed with the matching private key and verified locally.
 *
 * Mix this into any functional test class that uses GuiceOneAppPerSuite.
 */
trait AuthenticatedFunctionalTest extends GuiceOneAppPerSuite { self: org.scalatestplus.play.PlaySpec =>

  private val keyPair = KeyPairGenerator.getInstance("RSA").tap(_.initialize(2048)).generateKeyPair()
  protected val testPublicKey: RSAPublicKey = keyPair.getPublic.asInstanceOf[RSAPublicKey]
  protected val testPrivateKey: RSAPrivateKey = keyPair.getPrivate.asInstanceOf[RSAPrivateKey]

  private val testJwkProviderFactory: JwkProviderFactory = new JwkProviderFactory {
    def create(): JwkProvider = {
      val mockJwk = mock(classOf[Jwk])
      when(mockJwk.getPublicKey).thenReturn(testPublicKey)
      val provider = mock(classOf[JwkProvider])
      when(provider.get("test-kid")).thenReturn(mockJwk)
      provider
    }
  }

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .overrides(bind[JwkProviderFactory].toInstance(testJwkProviderFactory))
      .configure("play.filters.disabled" -> Seq("play.filters.csrf.CSRFFilter"))
      .build()

  /** Creates a valid test JWT signed with the local private key. */
  protected def testToken(email: String = "test@example.com", uid: String = "test-uid"): String =
    JWT.create()
      .withIssuer("https://securetoken.google.com/smeckles-app-11ca3")
      .withAudience("smeckles-app-11ca3")
      .withSubject(uid)
      .withClaim("email", email)
      .withKeyId("test-kid")
      .withExpiresAt(Date.from(Instant.now().plusSeconds(3600)))
      .sign(Algorithm.RSA256(testPublicKey, testPrivateKey))

  /** Authorization header with a valid test token. */
  protected def authHeader(email: String = "test@example.com"): (String, String) =
    "Authorization" -> s"Bearer ${testToken(email)}"
}
