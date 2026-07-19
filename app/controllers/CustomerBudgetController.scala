package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import auth.AuthenticatedAction

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class CustomerBudgetController @Inject()(
  val controllerComponents: ControllerComponents,
  authenticated: AuthenticatedAction
)(implicit ec: ExecutionContext) extends BaseController {

  def getBudgets(email: String): Action[AnyContent] = authenticated.async { _ =>
    Future.successful(NotImplemented(Json.obj("error" -> "Get budgets is not yet implemented")))
  }

  def create(email: String): Action[JsValue] = authenticated.async(parse.json) { _ =>
    Future.successful(NotImplemented(Json.obj("error" -> "Create budget is not yet implemented")))
  }

  def update(email: String, periodStart: LocalDate): Action[JsValue] = authenticated.async(parse.json) { _ =>
    Future.successful(NotImplemented(Json.obj("error" -> "Update budget is not yet implemented")))
  }

  def delete(email: String, periodStart: LocalDate): Action[AnyContent] = authenticated.async { _ =>
    Future.successful(NotImplemented(Json.obj("error" -> "Delete budget is not yet implemented")))
  }
}
