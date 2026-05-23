package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import play.api.mvc.BaseController
import models.ShoppingListItem
import models.requests.ShoppingListCreateRequest
import services.ShoppingListService

class ShoppingListController @Inject()(
  val controllerComponents: ControllerComponents,
  val service: ShoppingListService

) extends BaseController {

  def getShoppingList(email: String): Action[AnyContent] = Action {
    service.getShoppingList(email) match {
      case Left(errorMessage) => NotFound(Json.obj("error" -> errorMessage))
      case Right(shoppingList) => Ok(Json.toJson(shoppingList))
    }
  }

  def create(): Action[JsValue] = Action(parse.json) { request =>
    request.body.validate[ShoppingListCreateRequest] match {
      case JsError(errors) => BadRequest(Json.obj("error" -> "Invalid request format", "details" -> JsError.toJson(errors)))
      case JsSuccess(createRequest, _) =>
        service.create(createRequest.email, createRequest.name, createRequest.items) match {
          case Left(errorMessage) => Conflict(Json.obj("error" -> errorMessage))
          case Right(shoppingList) => Created(Json.toJson(shoppingList))
        }
      }
    }
}
