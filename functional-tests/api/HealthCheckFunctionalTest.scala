package api

import org.scalatestplus.play.*
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.*
import play.api.test.Helpers.*

class HealthCheckFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest {

  "HealthCheck" should {
    "return 200 OK with status 'ok' without authentication" in {
      val healthRequest = FakeRequest(GET, "/api/v1/health")
      val response = route(app, healthRequest).get
      status(response) mustBe OK
      contentAsJson(response) mustBe Json.obj("status" -> "ok")
    }
  }
}
