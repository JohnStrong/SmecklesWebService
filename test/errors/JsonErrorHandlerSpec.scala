package errors

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import play.api.test.FakeRequest
import play.api.libs.json.Json
import play.api.test.Helpers._
import ch.qos.logback.classic.{Level, Logger}
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

class JsonErrorHandlerSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private val handler = new JsonErrorHandler()
  private val fakeRequest = FakeRequest("GET", "/api/v1/test")

  private def withCapturedLogs(test: ListAppender[ILoggingEvent] => Any): Unit = {
    val loggerName = "errors.JsonErrorHandler"
    val logger = LoggerFactory.getLogger(loggerName).asInstanceOf[Logger]
    val appender = new ListAppender[ILoggingEvent]()
    appender.start()
    logger.addAppender(appender)
    try { test(appender) }
    finally { logger.detachAppender(appender) }
  }

  "onClientError" should {

    "return JSON error with the provided message" in {
      val result = handler.onClientError(fakeRequest, 400, "Invalid date format for 'dayDate': expected yyyy-MM-dd").futureValue

      result.header.status shouldBe 400
      val json = contentAsJson(scala.concurrent.Future.successful(result))
      (json \ "error").as[String] shouldBe "Invalid date format for 'dayDate': expected yyyy-MM-dd"
    }

    "return generic message when Play provides empty message" in {
      val result = handler.onClientError(fakeRequest, 404, "").futureValue

      result.header.status shouldBe 404
      val json = contentAsJson(scala.concurrent.Future.successful(result))
      (json \ "error").as[String] shouldBe "Unknown client error"
    }

    "preserve the status code from Play" in {
      val result = handler.onClientError(fakeRequest, 405, "Method not allowed").futureValue

      result.header.status shouldBe 405
    }
  }

  "onServerError" should {

    "return 500 with generic JSON error (no stack trace leaked)" in {
      withCapturedLogs { _ =>
        val exception = new RuntimeException("Database connection pool exhausted")
        val result = handler.onServerError(fakeRequest, exception).futureValue

        result.header.status shouldBe 500
        val json = contentAsJson(scala.concurrent.Future.successful(result))
        (json \ "error").as[String] shouldBe "Internal server error"
        contentAsString(scala.concurrent.Future.successful(result)) should not include "Database connection pool"
      }
    }

    "log the exception with request method and URI" in {
      withCapturedLogs { appender =>
        val exception = new RuntimeException("Something broke")
        handler.onServerError(fakeRequest, exception).futureValue

        val logs = appender.list
        logs.size shouldBe 1
        logs.get(0).getLevel shouldBe Level.ERROR
        logs.get(0).getMessage should include("Internal server error on GET /api/v1/test")
        logs.get(0).getThrowableProxy.getMessage shouldBe "Something broke"
      }
    }
  }
}
