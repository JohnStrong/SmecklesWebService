package repositories.shoppinglist

import models.{Customer, ShoppingListItem, ShoppingListWithItems}
import org.scalatest.{BeforeAndAfterEach, EitherValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import repositories.customer.SlickCustomerRepository

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
    // due to foreign key constraint between customers.email <=> shopping_lists.email , we must insert test customer entry
    customerRepository.create(Customer(email = shoppingList.email)).futureValue
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

}
