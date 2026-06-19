package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import services.CustomerService
import models.Customer
import auth.AuthenticatedAction

import scala.concurrent.{ExecutionContext, Future}

/**
 * REST controller for Customer operations.
 *
 * Uses Play's `Action.async` to return [[scala.concurrent.Future]][Result] — no thread
 * blocks waiting for the service response. The pattern is:
 *
 *  1. The service method returns `Future[Either[String, Customer]]`
 *  2. `.map` registers a callback that transforms the Either into an HTTP Result
 *     (e.g. Ok, NotFound, Conflict) once the Future completes
 *  3. Play's underlying Pekko HTTP layer observes the Future completion and writes
 *     the response to the client — no `Await.result` is ever called
 *
 * For synchronous early-exit paths (e.g. request validation failures), we wrap the
 * Result in `Future.successful` so both branches return the same `Future[Result]`
 * type that `Action.async` expects. This incurs no thread-pool overhead.
 *
 * @param controllerComponents Play's standard controller dependencies
 * @param authenticated Firebase Auth token verification action
 * @param customerService the business-logic layer for customer operations
 * @param ec the ExecutionContext used by `.map` callbacks on Futures
 */
@Singleton
class CustomerController @Inject()(
  val controllerComponents: ControllerComponents,
  authenticated: AuthenticatedAction,
  customerService: CustomerService
)(implicit ec: ExecutionContext) extends BaseController {

  def getCustomerByEmail(email: String): Action[AnyContent] = authenticated.async { _ =>
    customerService.findByEmail(email).map {
      case Left(errorMessage) => NotFound(Json.obj("error" -> errorMessage))
      case Right(customer) => Ok(Json.toJson(customer))
    }
  }

  def createCustomer(): Action[JsValue] = authenticated.async(parse.json) { authRequest =>
    (authRequest.body \ "email").asOpt[String].filter(_.trim.nonEmpty) match {
      case None => Future.successful { BadRequest(Json.obj("error" -> "Email is required")) }
      case Some(email) => customerService.createCustomer(email, authRequest.email).map {
        case Left(errorMessage) => Conflict(Json.obj("error" -> errorMessage))
        case Right(customer) => Created(Json.toJson(customer))
      }
    }
  }

  def deleteCustomer(email: String): Action[AnyContent] = authenticated.async { _ =>
    customerService.deleteCustomer(email).map {
      case Left(errorMessage) => NotFound(Json.obj("error" -> errorMessage))
      case Right(_) => NoContent // status-code: 204
    }
  }
}
