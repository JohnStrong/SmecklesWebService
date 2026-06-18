package repositories.shoppinglist

import models.{Customer, ShoppingListItem, ShoppingListWithItems}
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.db.slick.DatabaseConfigProvider
import repositories.customer.SlickCustomerRepository
import slick.jdbc.JdbcProfile

class SlickShoppingListRepositorySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with EitherValues
  with GuiceOneAppPerTest { // each test case creates a new guice app so data is not persisted cross tests

  // db timeout config for tests
  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  private def repository = app.injector.instanceOf[SlickShoppingListRepository]
  private def customerRepository = app.injector.instanceOf[SlickCustomerRepository]

  private val shoppingList = ShoppingListWithItems(
    email = "test@example.com",
    name = "test-1",
    items = List(
      ShoppingListItem(name = "Milk", quantity = 2),
      ShoppingListItem(name = "Bread", quantity = 1)
    ))

  private def withCustomer(test: => Unit): Unit = {
    // insert a fake user to satisfy FK constraint on customers.user_id
    val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
    val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig.profile.api.*
    val userId = dbConfig.db.run(
      sqlu"INSERT INTO users (email) VALUES ('test@user.com')"
        .andThen(sql"SELECT id FROM users WHERE email = 'test@user.com'".as[Long].head)
    ).futureValue
    // due to foreign key constraint between customers.email <=> shopping_lists.email , we must insert test customer entry
    customerRepository.create(Customer(email = shoppingList.email, userId = userId)).futureValue
    test
  }

  "create" should {
    "insert the shopping list and items into the db" in withCustomer {
      val result = repository.create(shoppingList).futureValue
      result.value.email shouldBe "test@example.com"
      result.value.name shouldBe "test-1"

      val actualStored = repository.findByIdentifier(result.value.email).futureValue
      actualStored.value shouldBe shoppingList
    }

    "return an error message if a shopping list already exists for the email" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.create(shoppingList).futureValue

      result.left.value should include("already exists")
    }
  }

  "findByIdentifier" should {
    "return the shopping list and items for an entry that exists in the db" in withCustomer {
      val result = repository.create(shoppingList).futureValue

      val stored = repository.findByIdentifier(result.value.email).futureValue

      stored.value shouldBe shoppingList
    }

    "return an error message if the shopping list is not found in the db" in {
      val stored = repository.findByIdentifier("doesnotexist@example.com").futureValue

      stored.left.value should include("No shopping list found")
    }
  }

  "findAllByIdentifier" should {
    "return Right with list of shopping lists for an existing email" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.findAllByIdentifier(shoppingList.email).futureValue

      result.value should have length 1
      result.value.head shouldBe shoppingList
    }

    "return Right with empty list when no customer exists with email" in {
      val result = repository.findAllByIdentifier("nonexistent@example.com").futureValue

      result.value shouldBe empty
    }

    "return Right with empty list when customer exists but has no shopping lists" in withCustomer {
      val result = repository.findAllByIdentifier(shoppingList.email).futureValue

      result.value shouldBe empty
    }
  }

}
