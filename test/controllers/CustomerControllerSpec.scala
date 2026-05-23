package controllers

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import play.api.test.*
import play.api.test.Helpers.*
import play.api.libs.json.*
import play.api.mvc.*
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import services.CustomerService
import models.Customer

class CustomerControllerSpec extends AnyWordSpec with Matchers {

  implicit private val system: ActorSystem = ActorSystem("test")
  implicit private val mat: Materializer = Materializer.matFromSystem

  private def createFixture() = {
    val mockService = mock(classOf[CustomerService])
    val stubComponents = Helpers.stubControllerComponents()
    val controller = new CustomerController(stubComponents, mockService)
    (controller, mockService)
  }

  private val testCustomer = Customer(email = "test@example.com")

  "getCustomerByEmail" should {

    "return 200 with customer JSON when found" in {
      val (controller, mockService) = createFixture()
      when(mockService.findByEmail("test@example.com")).thenReturn(Right(testCustomer))

      val result = controller.getCustomerByEmail("test@example.com").apply(FakeRequest())

      status(result) shouldBe OK
      (contentAsJson(result) \ "email").as[String] shouldBe "test@example.com"
    }

    "return 404 when customer not found" in {
      val (controller, mockService) = createFixture()
      when(mockService.findByEmail("missing@example.com"))
        .thenReturn(Left("Customer with email missing@example.com not found."))

      val result = controller.getCustomerByEmail("missing@example.com").apply(FakeRequest())

      status(result) shouldBe NOT_FOUND
      (contentAsJson(result) \ "error").as[String] should include("not found")
    }
  }

  "createCustomer" should {

    "return 201 with customer JSON on success" in {
      val (controller, mockService) = createFixture()
      when(mockService.createCustomer("new@example.com")).thenReturn(Right(testCustomer))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "new@example.com"))
      val result = call(controller.createCustomer(), request)

      status(result) shouldBe CREATED
      (contentAsJson(result) \ "email").as[String] shouldBe "test@example.com"
    }

    "return 400 when email is missing" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("name" -> "no email"))
      val result = call(controller.createCustomer(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 409 when customer already exists" in {
      val (controller, mockService) = createFixture()
      when(mockService.createCustomer("exists@example.com"))
        .thenReturn(Left("Customer with email exists@example.com already exists."))

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> "exists@example.com"))
      val result = call(controller.createCustomer(), request)

      status(result) shouldBe CONFLICT
      (contentAsJson(result) \ "error").as[String] should include("already exists")
    }

    "return 400 when email is empty string" ignore {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> ""))
      val result = call(controller.createCustomer(), request)

      status(result) shouldBe BAD_REQUEST
    }

    "return 400 when email is null" in {
      val (controller, _) = createFixture()

      val request = FakeRequest(POST, "/")
        .withHeaders("Content-Type" -> "application/json")
        .withBody(Json.obj("email" -> JsNull))
      val result = call(controller.createCustomer(), request)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
