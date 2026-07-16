package api

import org.scalatestplus.play.*
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class HttpInfraFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest {

  "JsonErrorHandler" should {

    "return JSON error for non-existent routes" in {
      val request = FakeRequest(GET, "/api/v1/nonexistent")
      val response = route(app, request).get
      status(response) mustBe NOT_FOUND
      (contentAsJson(response) \ "error").asOpt[String] mustBe defined
    }

    "return JSON error when route exists but method is not supported" in {
      val request = FakeRequest(PUT, "/api/v1/health")
      val response = route(app, request).get
      status(response) mustBe NOT_FOUND
      (contentAsJson(response) \ "error").asOpt[String] mustBe defined
    }
  }
}
