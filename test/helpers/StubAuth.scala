package helpers

import auth.{AuthenticatedAction, AuthenticatedRequest, JwkProviderFactory}
import com.auth0.jwk.JwkProvider
import play.api.Configuration
import play.api.mvc.*
import play.api.mvc.BodyParsers

import scala.concurrent.{ExecutionContext, Future}

/**
 * A stub AuthenticatedAction that bypasses token verification.
 * Every request passes through with dummy identity ("stub-uid", "stub@test.com").
 * Use in unit tests to isolate controller logic from auth concerns.
 *
 * parser is null-cast because invokeBlock is fully overridden — the parser field
 * is never accessed at runtime in these tests.
 */
object StubAuth {
  def action(implicit ec: ExecutionContext): AuthenticatedAction =
    new AuthenticatedAction(null.asInstanceOf[BodyParsers.Default], Configuration("auth.firebase.projectId" -> "test"),
      new JwkProviderFactory { def create(): JwkProvider = null }) {
      override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] =
        block(AuthenticatedRequest("stub-uid", "stub@test.com", request))
    }
}
