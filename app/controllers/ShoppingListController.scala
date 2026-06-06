package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import play.api.mvc.BaseController
import models.requests.ShoppingListCreateRequest
import services.ShoppingListService

import scala.concurrent.{ExecutionContext, Future}

class ShoppingListController @Inject()(
  val controllerComponents: ControllerComponents,
  val service: ShoppingListService
)(implicit ec: ExecutionContext) extends BaseController {

  @Deprecated
  def getShoppingList(email: String): Action[AnyContent] = Action.async {
    service.getShoppingList(email) map {
      case Left(errorMessage) => NotFound(Json.obj("error" -> errorMessage))
      case Right(shoppingList) => Ok(Json.toJson(shoppingList))
    }
  }

  def getShoppingLists(email: String): Action[AnyContent] = Action.async {
    service.getShoppingLists(email) map {
      case Left(errorMessage) => InternalServerError(Json.obj("error" -> errorMessage))
      case Right(shoppingList) => Ok(Json.toJson(shoppingList))
    }
  }

  def create(email: String): Action[JsValue] = Action.async(parse.json) { request =>
    request.body.validate[ShoppingListCreateRequest] match {
      case JsError(errors) => Future.successful {
          BadRequest(Json.obj("error" -> "Invalid request format", "details" -> JsError.toJson(errors)))
        }
      case JsSuccess(createRequest, _) =>
        service.create(email, createRequest.name, createRequest.items) map {
          case Left(errorMessage) => Conflict(Json.obj("error" -> errorMessage))
          case Right(shoppingList) => Created(Json.toJson(shoppingList))
        }
      }
    }
}
