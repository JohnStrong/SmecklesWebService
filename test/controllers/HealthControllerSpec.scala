package controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.*
import play.api.test.Helpers.*

import scala.concurrent.ExecutionContext

class HealthControllerSpec extends AnyWordSpec with Matchers {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val controller = new HealthController(Helpers.stubControllerComponents())

  "HealthController" should {
    "return 200 OK with status 'ok'" in {
      val result = controller.check().apply(FakeRequest())
      status(result) shouldBe Helpers.OK
      (contentAsJson(result) \ "status").as[String] shouldBe "ok"
    }
  }
}
