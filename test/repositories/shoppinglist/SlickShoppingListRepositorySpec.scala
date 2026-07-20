package repositories.shoppinglist

import models.{Customer, ShoppingListItem, ShoppingListWithItems}
import org.scalatest.EitherValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import repositories.RepositoryTestFixture
import repositories.customer.SlickCustomerRepository

import java.time.LocalDate

class SlickShoppingListRepositorySpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with EitherValues
  with GuiceOneAppPerTest
  with RepositoryTestFixture {

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  private def repository = app.injector.instanceOf[SlickShoppingListRepository]
  protected def customerRepository = app.injector.instanceOf[SlickCustomerRepository]

  private val shoppingList = ShoppingListWithItems(
    email = "test@example.com",
    name = "test-1",
    periodStart = LocalDate.of(2026, 7, 1),
    dayDate = LocalDate.of(2026, 7, 5),
    items = List(
      ShoppingListItem(name = "Milk", quantity = 2, currencyCode = "GBP", unitAmountMinor = 129L, lineAmountMinor = 258L),
      ShoppingListItem(name = "Bread", quantity = 1, currencyCode = "GBP", unitAmountMinor = 100L, lineAmountMinor = 100L)
    ))

  private def withCustomer(test: Long => Any): Unit = withCustomer(shoppingList.email)(test)
  private def withCustomer(test: => Any): Unit = withCustomer(shoppingList.email)(test)

  "create" should {
    "insert the shopping list and items into the db" in withCustomer {
      val result = repository.create(shoppingList).futureValue
      result.value.email shouldBe "test@example.com"
      result.value.name shouldBe "test-1"
      result.value.periodStart shouldBe LocalDate.of(2026, 7, 1)
      result.value.dayDate shouldBe LocalDate.of(2026, 7, 5)

      val actualStored = repository.findByEmail(result.value.email).futureValue
      actualStored.value shouldBe shoppingList
    }

    "return an error message if a shopping list with the same name already exists on the same day" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.create(shoppingList).futureValue

      result.left.value should include("already exists")
    }

    "allow creating two shopping lists with different names on the same day" in withCustomer {
      val firstList = shoppingList.copy(name = "Groceries")
      val secondList = shoppingList.copy(name = "Hardware")

      repository.create(firstList).futureValue.value.name shouldBe "Groceries"
      repository.create(secondList).futureValue.value.name shouldBe "Hardware"

      val all = repository.findAllByEmail(shoppingList.email).futureValue
      all.value should have length 2
    }

    "allow creating a shopping list with the same name on a different day" in withCustomer {
      val list1 = shoppingList.copy(dayDate = LocalDate.of(2026, 7, 5))
      val list2 = shoppingList.copy(dayDate = LocalDate.of(2026, 7, 12))

      repository.create(list1).futureValue.value.dayDate shouldBe LocalDate.of(2026, 7, 5)
      repository.create(list2).futureValue.value.dayDate shouldBe LocalDate.of(2026, 7, 12)

      val all = repository.findAllByEmail(shoppingList.email).futureValue
      all.value should have length 2
    }

    "reject creating a shopping list with the same name on the same day" in withCustomer {
      repository.create(shoppingList).futureValue

      val duplicate = shoppingList.copy(items = List(
        ShoppingListItem(name = "Eggs", quantity = 6, currencyCode = "GBP", unitAmountMinor = 50L, lineAmountMinor = 300L)
      ))
      val result = repository.create(duplicate).futureValue

      result.left.value should include("already exists")
    }

    "allow creating a shopping list with the same name for a different customer" in withCustomer { userId =>
      customerRepository.create(Customer(email = "other@example.com", userId = userId)).futureValue

      val firstList = shoppingList.copy(email = "test@example.com")
      val secondList = shoppingList.copy(email = "other@example.com")

      repository.create(firstList).futureValue.value.email shouldBe "test@example.com"
      repository.create(secondList).futureValue.value.email shouldBe "other@example.com"
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

  "deleteByEmailNameAndDay" should {
    "delete the shopping list when email, day, and name all match" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.deleteByEmailNameAndDay(shoppingList.email, shoppingList.name, shoppingList.dayDate).futureValue
      result.value shouldBe (())

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value shouldBe empty
    }

    "not delete when name does not match" in withCustomer {
      repository.create(shoppingList).futureValue

      repository.deleteByEmailNameAndDay(shoppingList.email, "Nonexistent", shoppingList.dayDate).futureValue

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value should have length 1
    }

    "not delete when email does not match" in withCustomer {
      repository.create(shoppingList).futureValue

      repository.deleteByEmailNameAndDay("other@example.com", shoppingList.name, shoppingList.dayDate).futureValue

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value should have length 1
    }

    "not delete when day does not match" in withCustomer {
      repository.create(shoppingList).futureValue

      repository.deleteByEmailNameAndDay(shoppingList.email, shoppingList.name, LocalDate.of(2026, 7, 20)).futureValue

      val stored = repository.findAllByEmail(shoppingList.email).futureValue
      stored.value should have length 1
    }

    "return Right(()) when no matching shopping list exists" in withCustomer {
      val result = repository.deleteByEmailNameAndDay("nobody@example.com", "Nonexistent", LocalDate.of(2026, 7, 5)).futureValue

      result.value shouldBe (())
    }

    "only delete the list on a specific day when same name exists on multiple days" in withCustomer {
      val july5 = shoppingList.copy(name = "Weekly Groceries", dayDate = LocalDate.of(2026, 7, 5))
      val july12 = shoppingList.copy(name = "Weekly Groceries", dayDate = LocalDate.of(2026, 7, 12))

      repository.create(july5).futureValue
      repository.create(july12).futureValue

      repository.deleteByEmailNameAndDay(
        shoppingList.email,
        "Weekly Groceries",
        LocalDate.of(2026, 7, 5)
      ).futureValue

      val remaining = repository.findAllByEmail(shoppingList.email).futureValue
      remaining.value should have length 1
      remaining.value.head.dayDate shouldBe LocalDate.of(2026, 7, 12)
    }
  }

  "updateItemStatus" should {

    "update an item from pending to completed" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.updateItemStatus("test@example.com", "test-1", "Milk", "completed").futureValue

      result.value.name shouldBe "Milk"
      result.value.status shouldBe "completed"
    }

    "update an item from completed back to pending" in withCustomer {
      repository.create(shoppingList).futureValue
      repository.updateItemStatus("test@example.com", "test-1", "Milk", "completed").futureValue

      val result = repository.updateItemStatus("test@example.com", "test-1", "Milk", "pending").futureValue

      result.value.status shouldBe "pending"
    }

    "return Left when shopping list does not exist" in withCustomer {
      val result = repository.updateItemStatus("test@example.com", "nonexistent", "Milk", "completed").futureValue

      result.left.value shouldBe "Item not found"
    }

    "return Left when item does not exist in the list" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.updateItemStatus("test@example.com", "test-1", "Nonexistent", "completed").futureValue

      result.left.value shouldBe "Item not found"
    }

    "return Right when item already has the requested status (idempotent)" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.updateItemStatus("test@example.com", "test-1", "Milk", "pending").futureValue

      result.value.status shouldBe "pending"
    }

    "preserve the item's monetary values after status update" in withCustomer {
      repository.create(shoppingList).futureValue

      val result = repository.updateItemStatus("test@example.com", "test-1", "Milk", "completed").futureValue

      result.value.quantity shouldBe 2
      result.value.currencyCode shouldBe "GBP"
      result.value.unitAmountMinor shouldBe 129L
      result.value.lineAmountMinor shouldBe 258L
    }
  }

}
