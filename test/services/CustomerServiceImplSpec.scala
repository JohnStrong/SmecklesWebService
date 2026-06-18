package services

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures
import models.{Customer, User}
import repositories.DataRepository
import repositories.customer.CustomerRepository

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

class CustomerServiceImplSpec extends AnyWordSpec with Matchers with ScalaFutures {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  /** Simple in-memory user repository for testing */
  private class FakeUserRepository extends DataRepository[String, User] {
    private val store = mutable.Map.empty[String, User]
    private var nextId = 1L

    override def create(payload: User): Future[Either[String, User]] = Future.successful {
      store.get(payload.email) match {
        case Some(_) => Left(s"User with email ${payload.email} already exists.")
        case None =>
          val user = User(userId = Some(nextId), email = payload.email)
          nextId += 1
          store.put(payload.email, user)
          Right(user)
      }
    }

    override def findByIdentifier(id: String): Future[Either[String, User]] = Future.successful {
      store.get(id).toRight(s"User with email '$id' not found.")
    }

    override def findAllByIdentifier(id: String): Future[Either[String, List[User]]] = ???
  }

  private def freshService() = new CustomerServiceImpl(new CustomerRepository(), new FakeUserRepository())

  "createCustomer" should {

    "return Right with new customer on success (creates user implicitly)" in {
      val service = freshService()
      val result = service.createCustomer("new@example.com", "auth@user.com").futureValue

      result.isRight shouldBe true
      result.toOption.get.email shouldBe "new@example.com"
      result.toOption.get.userId shouldBe 1L
    }

    "return Left with error when customer email already exists" in {
      val service = freshService()
      service.createCustomer("dup@example.com", "auth@user.com").futureValue

      val result = service.createCustomer("dup@example.com", "auth@user.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("already exists")
    }

    "reuse existing user on second call" in {
      val service = freshService()
      val first = service.createCustomer("c1@example.com", "auth@user.com").futureValue
      val second = service.createCustomer("c2@example.com", "auth@user.com").futureValue

      // same user id since same auth email
      first.toOption.get.userId shouldBe second.toOption.get.userId
    }
  }

  "findByEmail" should {

    "return Right with customer when found" in {
      val service = freshService()
      service.createCustomer("find@example.com", "auth@user.com").futureValue

      val result = service.findByEmail("find@example.com").futureValue

      result.isRight shouldBe true
      result.toOption.get.email shouldBe "find@example.com"
    }

    "return Left with error when not found" in {
      val service = freshService()
      val result = service.findByEmail("nonexistent@example.com").futureValue

      result shouldBe a[Left[_, _]]
      result.left.toOption.get should include("not found")
    }
  }
}
