package controllers

import play.api.mvc.*
import play.api.libs.json.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HealthController @Inject()(
  val controllerComponents: ControllerComponents,
)(implicit ec: ExecutionContext) extends BaseController {

  def check(): Action[AnyContent] = Action.async {
    Future.successful { Ok(Json.obj("status" -> "ok")) }
  }
}
