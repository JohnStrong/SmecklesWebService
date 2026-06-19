package repositories.customer

import models.Customer

import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Span, Seconds, Millis}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.JdbcProfile

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

  /** Insert a fake user row to satisfy the FK constraint on customers.user_id */
  private def insertTestUser(): Long = {
    val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
    val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig.profile.api.*
    val insertAction = sqlu"INSERT INTO users (email) VALUES ('test@user.com')"
    val idAction = sql"SELECT id FROM users WHERE email = 'test@user.com'".as[Long].head
    dbConfig.db.run(insertAction.andThen(idAction)).futureValue
  }

  "create" should {
    "persist customer to the datastore and return it" in {
      val userId = insertTestUser()
      val testCustomer = Customer(email = "test@example.com", userId = userId)
      val result = repository.create(testCustomer).futureValue
      result.value shouldBe testCustomer

      val theCustomer = repository.findByIdentifier(testCustomer.email).futureValue
      theCustomer.value shouldBe testCustomer
    }

    "return an error message if customer already exists in the datastore" in {
      val userId = insertTestUser()
      val testCustomer = Customer(email = "test@example.com", userId = userId)
      repository.create(testCustomer).futureValue

      val result = repository.create(testCustomer).futureValue

      result.left.value should include("already exists")
    }
  }

  "findByIdentifier" should {
    "return the customer if it exists in the datastore" in {
      val userId = insertTestUser()
      val testCustomer = Customer(email = "test@example.com", userId = userId)
      repository.create(testCustomer).futureValue

      val result = repository.findByIdentifier(testCustomer.email).futureValue

      result.value shouldBe testCustomer
    }

    "return an error message if there is no customer with the email" in {
      val result = repository.findByIdentifier("nonexistent@example.com").futureValue

      result.left.value should include("not found")
    }
  }

  "delete" should {
    "delete existing customer and confirm it is no longer in the datastore" in {
      val userId = insertTestUser()
      val testCustomer = Customer(email = "delete@example.com", userId = userId)
      repository.create(testCustomer).futureValue

      val result = repository.delete("delete@example.com").futureValue

      result.value shouldBe ()
      repository.findByIdentifier("delete@example.com").futureValue.left.value should include("not found")
    }

    "return error when customer does not exist and leave datastore unchanged" in {
      val userId = insertTestUser()
      val existing = Customer(email = "keep@example.com", userId = userId)
      repository.create(existing).futureValue

      val result = repository.delete("nonexistent@example.com").futureValue

      result.left.value should include("not found")
      repository.findByIdentifier("keep@example.com").futureValue.value shouldBe existing
    }
  }
}
