package controllers

import javax.inject.*
import play.api.mvc.*
import play.api.libs.json.*
import play.api.mvc.BaseController
import models.requests.ShoppingListCreateRequest
import models.ShoppingListWithItems
import services.ShoppingListService
import auth.AuthenticatedAction

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

class ShoppingListController @Inject()(
  val controllerComponents: ControllerComponents,
  authenticated: AuthenticatedAction,
  val service: ShoppingListService
)(implicit ec: ExecutionContext) extends BaseController {

  @Deprecated
  def getShoppingList(email: String): Action[AnyContent] = authenticated.async { _ =>
    service.getShoppingList(email) map {
      case Left(errorMessage) => NotFound(Json.obj("error" -> errorMessage))
      case Right(shoppingList) => Ok(Json.toJson(shoppingList))
    }
  }

  def getShoppingLists(email: String): Action[AnyContent] = authenticated.async { _ =>
    service.getShoppingLists(email) map {
      case Left(errorMessage) => InternalServerError(Json.obj("error" -> errorMessage))
      case Right(shoppingList) => Ok(Json.toJson(shoppingList))
    }
  }

  def create(email: String): Action[JsValue] = authenticated.async(parse.json) { request =>
    request.body.validate[ShoppingListCreateRequest] match {
      case JsError(errors) => Future.successful {
          BadRequest(Json.obj("error" -> "Invalid request format", "details" -> JsError.toJson(errors)))
        }
      case JsSuccess(createRequest, _) =>
        val shoppingList = createShoppingListFromReq(email, createRequest)
        service.create(shoppingList) map {
          case Left(errorMessage) => Conflict(Json.obj("error" -> errorMessage))
          case Right(shoppingList) => Created(Json.toJson(shoppingList))
        }
      }
    }

  def deleteV2(email: String, dayDate: LocalDate, name: String) = authenticated.async { _ =>
    service.delete(email, dayDate, name).map {
      case Right(_) => NoContent
      case Left(errorMessage) => InternalServerError(Json.obj("error" -> errorMessage))
    }
  }

  def updateItemStatus(email: String, name: String, itemName: String): Action[JsValue] = authenticated.async(parse.json) { request =>
    val statusOpt = (request.body \ "status").asOpt[String]
    statusOpt match {
      case None => Future.successful(BadRequest(Json.obj("error" -> "Invalid status value")))
      case Some(status) =>
        service.updateItemStatus(email, name, itemName, status).map {
          case Right(item) => Ok(Json.toJson(item))
          case Left(msg) if msg == "Invalid status value" => BadRequest(Json.obj("error" -> msg))
          case Left(msg) if msg == "Item not found" => NotFound(Json.obj("error" -> msg))
          case Left(msg) => InternalServerError(Json.obj("error" -> msg))
        }
    }
  }

  private def createShoppingListFromReq(email: String, request: ShoppingListCreateRequest): ShoppingListWithItems = {
    ShoppingListWithItems(
      email = email,
      name = request.name, 
      dayDate = request.dayDate, 
      periodStart = request.periodStart,
      items = request.items
    )
  }
}
