package api

import org.scalatestplus.play.*
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Span, Seconds, Millis}
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

import java.util.concurrent.{CountDownLatch, Executors}
import scala.concurrent.{ExecutionContext, Future}

class CustomerConcurrencyFunctionalTest extends PlaySpec with AuthenticatedFunctionalTest with ScalaFutures {

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(10))

  "CustomerController concurrency" should {

    "allow only one create to succeed when 10 concurrent requests use the same email" in {
      val requests = (1 to 10).map { _ =>
        FakeRequest(POST, "/api/v1/customers")
          .withHeaders(authHeader(), "Content-Type" -> "application/json")
          .withBody(Json.obj("email" -> "race@test.com"))
      }
      val latch = new CountDownLatch(10)

      val results = Future.sequence(requests.map { req =>
        Future { latch.countDown(); latch.await() }
          .flatMap(_ => Future.successful(status(route(app, req).get)))
      }).futureValue

      results.count(_ == CREATED) mustBe 1
      results.count(_ == CONFLICT) mustBe 9

      val get = FakeRequest(GET, "/api/v1/customers/race@test.com").withHeaders(authHeader())
      status(route(app, get).get) mustBe OK
      contentAsJson(route(app, get).get) mustBe Json.obj("email" -> "race@test.com")
    }

    "handle 10 concurrent deletes and confirm resource is gone" in {
      val create = FakeRequest(POST, "/api/v1/customers")
        .withHeaders(authHeader(), "Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "concurrent-delete@test.com"))
      status(route(app, create).get) mustBe CREATED

      val requests = (1 to 10).map { _ =>
        FakeRequest(DELETE, "/api/v1/customers/concurrent-delete@test.com")
          .withHeaders(authHeader())
      }
      val latch = new CountDownLatch(10)

      val results = Future.sequence(requests.map { req =>
        Future { latch.countDown(); latch.await() }
          .flatMap(_ => Future.successful(status(route(app, req).get)))
      }).futureValue

      results.count(_ == NO_CONTENT) mustBe 1
      results.count(_ == NOT_FOUND) mustBe 9

      val get = FakeRequest(GET, "/api/v1/customers/concurrent-delete@test.com")
        .withHeaders(authHeader())
      status(route(app, get).get) mustBe NOT_FOUND
    }
  }
}
