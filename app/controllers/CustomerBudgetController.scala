package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import models.Budget
import models.requests.CustomerBudgetCreateRequest
import services.BudgetService
import auth.AuthenticatedAction

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class CustomerBudgetController @Inject()(
  val controllerComponents: ControllerComponents,
  authenticated: AuthenticatedAction,
  budgetService: BudgetService
)(implicit ec: ExecutionContext) extends BaseController {

  def getBudgets(email: String): Action[AnyContent] = authenticated.async { _ =>
    budgetService.getBudgets(email).map {
      case Right(budgets) => Ok(Json.toJson(budgets))
      case Left(errorMessage) => InternalServerError(Json.obj("error" -> errorMessage))
    }
  }

  def create(email: String): Action[JsValue] = authenticated.async(parse.json) { request =>
    request.body.validate[CustomerBudgetCreateRequest] match {
      case JsError(errors) => Future.successful {
        BadRequest(Json.obj("error" -> "Invalid request format", "details" -> JsError.toJson(errors)))
      }
      case JsSuccess(createRequest, _) =>
        val budget = Budget(
          email = email,
          periodStart = createRequest.periodStart,
          periodEnd = createRequest.periodEnd,
          amountMinor = createRequest.amountMinor,
          currencyCode = createRequest.currencyCode
        )
        budgetService.create(budget).map {
          case Right(created) => Created(Json.toJson(created))
          case Left(msg) if msg.contains("overlaps") => Conflict(Json.obj("error" -> msg))
          case Left(msg) => InternalServerError(Json.obj("error" -> msg))
        }
    }
  }

  def update(email: String, periodStart: LocalDate): Action[JsValue] = authenticated.async(parse.json) { _ =>
    Future.successful(NotImplemented(Json.obj("error" -> "Update budget is not yet implemented")))
  }

  def delete(email: String, periodStart: LocalDate): Action[AnyContent] = authenticated.async { _ =>
    Future.successful(NotImplemented(Json.obj("error" -> "Delete budget is not yet implemented")))
  }
}
