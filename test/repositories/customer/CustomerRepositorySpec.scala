package repositories.customer

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import models.Customer
import org.scalatest.{BeforeAndAfterEach, EitherValues}

class CustomerRepositorySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with BeforeAndAfterEach
  with EitherValues {

  private val testCustomer = Customer(email = "test@example.com", userId = 1L)
  private var customerRepository: CustomerRepository = _

  override def beforeEach(): Unit = {
    customerRepository = CustomerRepository()
  }

  "create" should {
    "persist the customer in the repo and return it" in {
      val result = customerRepository.create(testCustomer).futureValue
      result.value shouldBe testCustomer

      val actualStored = customerRepository.findByIdentifier(testCustomer.email).futureValue
      actualStored.value shouldBe testCustomer
    }

    "return an error message if customer already exists in repo" in {
      customerRepository.create(testCustomer).futureValue

      val result = customerRepository.create(testCustomer).futureValue

      result.left.value should include("already exists")
    }
  }

  "findByIdentifier" should {
    "return the customer if it exists in the repo" in {
      customerRepository.create(testCustomer).futureValue

      val result = customerRepository.findByIdentifier(testCustomer.email).futureValue

      result.value shouldBe testCustomer
    }

    "return an error message if there is no customer with the email" in {
      val result = customerRepository.findByIdentifier("doesNotExist@example.com").futureValue
      result.left.value should include("not found")
    }
  }
}
