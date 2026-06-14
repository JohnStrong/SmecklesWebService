package auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import play.api.Configuration
import play.api.mvc.*
import play.api.mvc.Results.*
import play.api.libs.json.Json

import java.security.interfaces.RSAPublicKey
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Wraps a verified request with the authenticated user's identity. */
case class AuthenticatedRequest[A](userId: String, email: String, request: Request[A])
  extends WrappedRequest[A](request)

/**
 * Play ActionBuilder that verifies Firebase Auth ID tokens on incoming requests.
 *
 * Usage in controllers:
 * {{{
 *   def myEndpoint() = authenticated.async { request =>
 *     // request.userId — Firebase UID
 *     // request.email  — user's email from token claims
 *   }
 * }}}
 *
 * Verification steps:
 *  1. Extracts the Bearer token from the `Authorization` header
 *  2. Fetches Google's public signing keys via the injected [[JwkProviderFactory]]
 *  3. Verifies RSA256 signature, issuer, audience, and expiry
 *  4. Extracts `sub` (Firebase UID) and `email` claims
 *
 * Returns 401 Unauthorized with a JSON error body if the token is missing, malformed, or invalid.
 */
@Singleton
class AuthenticatedAction @Inject()(
  val parser: BodyParsers.Default,
  config: Configuration,
  jwkProviderFactory: JwkProviderFactory
)(implicit val executionContext: ExecutionContext) extends ActionBuilder[AuthenticatedRequest, AnyContent] {

  private val projectId: String = config.get[String]("auth.firebase.projectId")
  private val jwkProvider = jwkProviderFactory.create()

  /**
   * Intercepts each request, verifies the Firebase ID token, and either
   * passes an [[AuthenticatedRequest]] to the controller action or short-circuits with 401.
   */
  override def invokeBlock[A](
     request: Request[A],
     block: AuthenticatedRequest[A] => Future[Result]
  ): Future[Result] = {
    extractBearerToken(request) match
      case None =>
        Future.successful(Unauthorized(Json.obj("error" -> "Missing or malformed Authorization header")))
      case Some(token) =>
        verifyToken(token) match
          case Success(claims) =>
            block(AuthenticatedRequest(claims.userId, claims.email, request))
          case Failure(ex) =>
            Future.successful(Unauthorized(Json.obj("error" -> s"Invalid token: ${ex.getMessage}")))
  }

  /** Extracts the raw JWT string from an "Authorization: Bearer <token>" header. */
  private def extractBearerToken(request: RequestHeader): Option[String] =
    request.headers.get("Authorization")
      .filter(_.startsWith("Bearer "))
      .map(_.stripPrefix("Bearer "))

  private case class TokenClaims(userId: String, email: String)

  /**
   * Verifies a Firebase ID token against Google's public keys.
   *
   * Checks:
   *  - RSA256 signature (using the key matching the token's `kid` header)
   *  - Issuer is `https://securetoken.google.com/<projectId>`
   *  - Audience matches our Firebase project ID
   *  - Token has not expired (handled automatically by the JWT library)
   *
   * @return [[Success]] with extracted claims, or [[Failure]] if verification fails.
   */
  private def verifyToken(token: String): Try[TokenClaims] = Try {
    val decoded = JWT.decode(token)
    val jwk = jwkProvider.get(decoded.getKeyId)
    val publicKey = jwk.getPublicKey.asInstanceOf[RSAPublicKey]

    val verified = JWT.require(Algorithm.RSA256(publicKey, null))
      .withIssuer(s"https://securetoken.google.com/$projectId")
      .withAudience(projectId)
      .build()
      .verify(token)

    TokenClaims(
      userId = verified.getSubject,
      email = verified.getClaim("email").asString
    )
  }
}
