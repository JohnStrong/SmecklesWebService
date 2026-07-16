package errors

import play.api.http.HttpErrorHandler
import play.api.mvc._
import play.api.libs.json.Json
import play.api.Logging
import scala.concurrent.Future

/**
 * Returns all HTTP errors as JSON objects: {"error": "..."}.
 * This ensures API consumers always receive a consistent JSON response,
 * even for routing failures, path binding errors, or unexpected server errors.
 */
class JsonErrorHandler extends HttpErrorHandler with Logging {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    val errorMessage = if (message.nonEmpty) message else "Unknown client error"
    Future.successful(Results.Status(statusCode)(Json.obj("error" -> errorMessage)))
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(s"Internal server error on ${request.method} ${request.uri}", exception)
    Future.successful(Results.InternalServerError(Json.obj("error" -> "Internal server error")))
  }
}
