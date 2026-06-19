package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import models.{Customer, User}
import repositories.DataRepository

import scala.concurrent.{ExecutionContext, Future}

class CustomerServiceImplSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  private val testCustomer = Customer(email = "test@example.com", userId = 1L)
  private val testUser = User(userId = Some(1L), email = "auth@user.com")

  private def freshService() = {
    val mockCustomerRepo = mock(classOf[DataRepository[String, Customer]])
    val mockUserRepo = mock(classOf[DataRepository[String, User]])
    (new CustomerServiceImpl(mockCustomerRepo, mockUserRepo), mockCustomerRepo, mockUserRepo)
  }

  "createCustomer" should {

    "return Right with new customer when user already exists" in {
      val (service, mockCustomerRepo, mockUserRepo) = freshService()
      when(mockUserRepo.findByIdentifier("auth@user.com")).thenReturn(Future.successful(Right(testUser)))
      when(mockCustomerRepo.create(any[Customer]())).thenReturn(Future.successful(Right(testCustomer)))

      val result = service.createCustomer("test@example.com", "auth@user.com").futureValue

      result shouldBe Right(testCustomer)
    }

    "create user implicitly when user does not exist" in {
      val (service, mockCustomerRepo, mockUserRepo) = freshService()
      when(mockUserRepo.findByIdentifier("auth@user.com")).thenReturn(Future.successful(Left("not found")))
      when(mockUserRepo.create(any[User]())).thenReturn(Future.successful(Right(testUser)))
      when(mockCustomerRepo.create(any[Customer]())).thenReturn(Future.successful(Right(testCustomer)))

      val result = service.createCustomer("test@example.com", "auth@user.com").futureValue

      result shouldBe Right(testCustomer)
    }

    "return Left when customer email already exists" in {
      val (service, mockCustomerRepo, mockUserRepo) = freshService()
      when(mockUserRepo.findByIdentifier("auth@user.com")).thenReturn(Future.successful(Right(testUser)))
      when(mockCustomerRepo.create(any[Customer]()))
        .thenReturn(Future.successful(Left("Customer with email test@example.com already exists.")))

      val result = service.createCustomer("test@example.com", "auth@user.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }

    "return Left when user creation fails" in {
      val (service, _, mockUserRepo) = freshService()
      when(mockUserRepo.findByIdentifier("auth@user.com")).thenReturn(Future.successful(Left("not found")))
      when(mockUserRepo.create(any[User]())).thenReturn(Future.successful(Left("DB error")))

      val result = service.createCustomer("test@example.com", "auth@user.com").futureValue

      result shouldBe Left("DB error")
    }
  }

  "findByEmail" should {

    "return Right with customer when found" in {
      val (service, mockCustomerRepo, _) = freshService()
      when(mockCustomerRepo.findByIdentifier("test@example.com")).thenReturn(Future.successful(Right(testCustomer)))

      val result = service.findByEmail("test@example.com").futureValue

      result shouldBe Right(testCustomer)
    }

    "return Left with error when not found" in {
      val (service, mockCustomerRepo, _) = freshService()
      when(mockCustomerRepo.findByIdentifier("missing@example.com"))
        .thenReturn(Future.successful(Left("Customer with email 'missing@example.com' not found.")))

      val result = service.findByEmail("missing@example.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("not found")
    }
  }

  "deleteCustomer" should {

    "return Right(()) when repository deletes successfully" in {
      val (service, mockCustomerRepo, _) = freshService()
      when(mockCustomerRepo.delete("test@example.com")).thenReturn(Future.successful(Right(())))

      val result = service.deleteCustomer("test@example.com").futureValue

      result shouldBe Right(())
    }

    "return Left with error when customer does not exist" in {
      val (service, mockCustomerRepo, _) = freshService()
      when(mockCustomerRepo.delete("missing@example.com"))
        .thenReturn(Future.successful(Left("Customer with email 'missing@example.com' not found.")))

      val result = service.deleteCustomer("missing@example.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("not found")
    }
  }
}
