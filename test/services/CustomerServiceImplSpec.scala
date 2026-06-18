package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import models.Customer
import repositories.customer.CustomerRepository

class CustomerServiceImplSpec extends AnyWordSpec with Matchers with ScalaFutures {

  private def freshService() = new CustomerServiceImpl(new CustomerRepository())

  "createCustomer" should {

    "return Right with new customer on success" in {
      val service = freshService()
      val result = service.createCustomer("new@example.com", 1L).futureValue

      result shouldBe Right(Customer("new@example.com", 1L))
    }

    "return Left with error when email already exists" in {
      val service = freshService()
      service.createCustomer("dup@example.com", 1L).futureValue

      val result = service.createCustomer("dup@example.com", 1L).futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }
  }

  "findByEmail" should {

    "return Right with customer when found" in {
      val service = freshService()
      service.createCustomer("find@example.com", 1L).futureValue

      val result = service.findByEmail("find@example.com").futureValue

      result shouldBe Right(Customer("find@example.com", 1L))
    }

    "return Left with error when not found" in {
      val service = freshService()
      val result = service.findByEmail("nonexistent@example.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("not found")
    }
  }
}
