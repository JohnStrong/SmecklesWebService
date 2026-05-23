package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import models.Customer

class CustomerServiceImplSpec extends AnyWordSpec with Matchers {

  private def freshService() = new CustomerServiceImpl()

  "createCustomer" should {

    "return Right with new customer on success" in {
      val service = freshService()
      val result = service.createCustomer("new@example.com")

      result shouldBe Right(Customer("new@example.com"))
    }

    "return Left with error when email already exists" in {
      val service = freshService()
      service.createCustomer("dup@example.com")

      val result = service.createCustomer("dup@example.com")

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }
  }

  "findByEmail" should {

    "return Right with customer when found" in {
      val service = freshService()
      service.createCustomer("find@example.com")

      val result = service.findByEmail("find@example.com")

      result shouldBe Right(Customer("find@example.com"))
    }

    "return Left with error when not found" in {
      val service = freshService()
      val result = service.findByEmail("nonexistent@example.com")

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("not found")
    }
  }
}
