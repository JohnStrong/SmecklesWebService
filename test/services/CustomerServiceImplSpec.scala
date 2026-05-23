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

      result shouldBe a[Right[_, _]]
      result.toOption.get.email shouldBe "new@example.com"
    }

    "return Left with error when email already exists" in {
      val service = freshService()
      service.createCustomer("dup@example.com")

      val result = service.createCustomer("dup@example.com")

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }

    "assign a unique ID to each customer" in {
      val service = freshService()
      val c1 = service.createCustomer("a@example.com").toOption.get
      val c2 = service.createCustomer("b@example.com").toOption.get

      c1.id should not be c2.id
    }
  }

  "findById" should {

    "return Right with customer when found" in {
      val service = freshService()
      val created = service.createCustomer("find@example.com").toOption.get

      val result = service.findById(created.id.toString)

      result shouldBe Right(created)
    }

    "return Left with error when not found" in {
      val service = freshService()
      val result = service.findById("nonexistent-id")

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("not found")
    }
  }
}
