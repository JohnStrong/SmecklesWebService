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
  with GuiceOneAppPerTest {

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
      ShoppingListItem(quantity = 2, currencyCode = "GBP", unitAmountMinor = 129L, lineAmountMinor = 258L),
      ShoppingListItem(quantity = 1, currencyCode = "GBP", unitAmountMinor = 100L, lineAmountMinor = 100L)
    ))

  private def withCustomer(test: Long => Any): Unit = {
    val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
    val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig.profile.api.*
    val userId = dbConfig.db.run(
      sqlu"INSERT INTO users (email) VALUES ('test@user.com')"
        .andThen(sql"SELECT id FROM users WHERE email = 'test@user.com'".as[Long].head)
    ).futureValue
    customerRepository.create(Customer(email = shoppingList.email, userId = userId)).futureValue
    test(userId)
  }

  private def withCustomer(test: => Any): Unit = withCustomer((_: Long) => test)

  "create" should {
    "insert the shopping list and items into the db" in withCustomer {
      val result = repository.create(shoppingList).futureValue
      result.value.email shouldBe "test@example.com"
      result.value.name shouldBe "test-1"

      val actualStored = repository.findByEmail(result.value.email).futureValue
      actualStored.value shouldBe shoppingList
    }

    "return an error message if a shopping list already exists for the email" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.create(shoppingList).futureValue

      result.left.value should include("already exists")
    }

    "allow creating two shopping lists with different names for the same customer" in withCustomer {
      val firstList = shoppingList.copy(name = "Groceries")
      val secondList = shoppingList.copy(name = "Hardware")

      repository.create(firstList).futureValue.value.name shouldBe "Groceries"
      repository.create(secondList).futureValue.value.name shouldBe "Hardware"

      val all = repository.findAllByEmail(shoppingList.email).futureValue
      all.value should have length 2
    }

    "reject creating a shopping list with the same name for the same customer" in withCustomer {
      repository.create(shoppingList).futureValue

      val duplicate = shoppingList.copy(items = List(
        ShoppingListItem(quantity = 6, currencyCode = "GBP", unitAmountMinor = 50L, lineAmountMinor = 300L)
      ))
      val result = repository.create(duplicate).futureValue

      result.left.value should include("already exists")
    }

    "allow creating a shopping list with the same name for a different customer" in withCustomer { userId =>
      customerRepository.create(Customer(email = "other@example.com", userId = userId)).futureValue

      val firstList = ShoppingListWithItems("test@example.com", "Groceries", List(
        ShoppingListItem(quantity = 1, currencyCode = "GBP", unitAmountMinor = 129L, lineAmountMinor = 129L)
      ))
      val secondList = ShoppingListWithItems("other@example.com", "Groceries", List(
        ShoppingListItem(quantity = 2, currencyCode = "GBP", unitAmountMinor = 100L, lineAmountMinor = 200L)
      ))

      repository.create(firstList).futureValue.value.name shouldBe "Groceries"
      repository.create(secondList).futureValue.value.name shouldBe "Groceries"
    }
  }

  "findByEmail" should {
    "return the shopping list and items for an entry that exists in the db" in withCustomer {
      val result = repository.create(shoppingList).futureValue

      val stored = repository.findByEmail(result.value.email).futureValue

      stored.value shouldBe shoppingList
    }

    "return an error message if the shopping list is not found in the db" in {
      val stored = repository.findByEmail("doesnotexist@example.com").futureValue

      stored.left.value should include("No shopping list found")
    }
  }

  "findAllByEmail" should {
    "return Right with list of shopping lists for an existing email" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.findAllByEmail(shoppingList.email).futureValue

      result.value should have length 1
      result.value.head shouldBe shoppingList
    }

    "return Right with empty list when no customer exists with email" in {
      val result = repository.findAllByEmail("nonexistent@example.com").futureValue

      result.value shouldBe empty
    }

    "return Right with empty list when customer exists but has no shopping lists" in withCustomer {
      val result = repository.findAllByEmail(shoppingList.email).futureValue

      result.value shouldBe empty
    }
  }

  "deleteByEmailAndName" should {
    "delete the shopping list when both email and name match" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.deleteByEmailAndName(shoppingList.email, shoppingList.name).futureValue
      result.value shouldBe (())

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value shouldBe empty
    }

    "not delete when only email matches but name does not" in withCustomer {
      repository.create(shoppingList).futureValue

      repository.deleteByEmailAndName(shoppingList.email, "Nonexistent").futureValue

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value should have length 1
    }

    "not delete when only name matches but email does not" in withCustomer {
      repository.create(shoppingList).futureValue

      repository.deleteByEmailAndName("other@example.com", shoppingList.name).futureValue

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value should have length 1
    }

    "return Right(()) when no matching shopping list exists" in withCustomer {
      val result = repository.deleteByEmailAndName("nobody@example.com", "Nonexistent").futureValue

      result.value shouldBe (())
    }
  }

}
