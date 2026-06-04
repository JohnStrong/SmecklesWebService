package repositories.customer

import models.Customer

import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Span, Seconds, Millis}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest

class SlickCustomerRepositorySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with EitherValues
  with GuiceOneAppPerTest { // each test case creates a new guice app so data is not persisted cross tests

  // db timeout config for tests
  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  private def repository = app.injector.instanceOf[SlickCustomerRepository]

  private val testCustomer = Customer(email = "test@example.com")

  "create" should {
    "persist customer to the datastore and return it" in {
      val result = repository.create(testCustomer).futureValue
      result.value shouldBe testCustomer

      val theCustomer = repository.findByIdentifier(testCustomer.email).futureValue
      theCustomer.value shouldBe testCustomer
    }

    "return an error message if customer already exists in the datastore" in {
      repository.create(testCustomer).futureValue

      val result = repository.create(testCustomer).futureValue

      result.left.value should include("already exists")
    }
  }

  "findByIdentifier" should {
    "return the customer if it exists in the datastore" in {
      repository.create(testCustomer).futureValue

      val result = repository.findByIdentifier(testCustomer.email).futureValue

      result.value shouldBe testCustomer
    }

    "return an error message if there is no customer with the email" in {
      val result = repository.findByIdentifier("nonexistent@example.com").futureValue

      result.left.value should include("not found")
    }
  }
}
