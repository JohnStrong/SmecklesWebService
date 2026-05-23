package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import services.CustomerService
import models.Customer

@Singleton
class CustomerController @Inject()(
  val controllerComponents: ControllerComponents,
  customerService: CustomerService
) extends BaseController {

  def getCustomerByEmail(email: String): Action[AnyContent] = Action {
    customerService.findByEmail(email) match {
      case Left(errorMessage) => NotFound(Json.obj("error" -> errorMessage))
      case Right(customer) => Ok(Json.toJson(customer))
    }
  }

  def createCustomer(): Action[JsValue] = Action(parse.json) { request =>
    (request.body \ "email").asOpt[String].filter(_.trim.nonEmpty) match {
      case None => BadRequest(Json.obj("error" -> "Email is required"))
      case Some(email) => customerService.createCustomer(email) match {
        case Left(errorMessage) => Conflict(Json.obj("error" -> errorMessage))
        case Right(customer) => Created(Json.toJson(customer))
      }
    }
  }
}
