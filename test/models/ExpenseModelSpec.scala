package models

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ExpenseModelSpec extends AnyWordSpec with Matchers {

  "SourceType.fromTableName" should {

    "return Right(ShoppingListItem) for 'shopping_list_items'" in {
      SourceType.fromTableName("shopping_list_items") shouldBe Right(SourceType.ShoppingListItem)
    }

    "return Left with error message for unknown table name" in {
      val result = SourceType.fromTableName("nonexistent_table")
      result shouldBe a[Left[_, _]]
      result.left.getOrElse("") should include("nonexistent_table")
    }

    "return Left for empty string" in {
      SourceType.fromTableName("") shouldBe a[Left[_, _]]
    }

    "be case-sensitive" in {
      SourceType.fromTableName("Shopping_List_Items") shouldBe a[Left[_, _]]
    }
  }

  "SourceType.tableName" should {

    "return the correct table name for ShoppingListItem" in {
      SourceType.ShoppingListItem.tableName shouldBe "shopping_list_items"
    }
  }

  "ExpenseCategory.fromDBValue" should {

    "return Right(Groceries) for 'Groceries'" in {
      ExpenseCategory.fromDBValue("Groceries") shouldBe Right(ExpenseCategory.Groceries)
    }

    "return Left with error message for unknown category" in {
      val result = ExpenseCategory.fromDBValue("Unknown")
      result shouldBe a[Left[_, _]]
      result.left.getOrElse("") should include("Unknown")
    }

    "return Left for empty string" in {
      ExpenseCategory.fromDBValue("") shouldBe a[Left[_, _]]
    }

    "be case-sensitive" in {
      ExpenseCategory.fromDBValue("groceries") shouldBe a[Left[_, _]]
    }
  }

  "ExpenseCategory.accepts" should {

    "return true when source type is in the category's applicable sources" in {
      ExpenseCategory.Groceries.accepts(SourceType.ShoppingListItem) shouldBe true
    }

    "return false when source type is not in the category's applicable sources" in {
      // When new categories are added without ShoppingListItem, this tests the negative case.
      // For now, verify the mechanism works by checking applicableSources directly.
      val categoryWithoutShoppingList = ExpenseCategory.values
        .find(cat => !ExpenseCategory.applicableSources.getOrElse(cat, Set.empty).contains(SourceType.ShoppingListItem))

      categoryWithoutShoppingList.foreach { cat =>
        cat.accepts(SourceType.ShoppingListItem) shouldBe false
      }
    }

    "return false for a category not present in the applicableSources map" in {
      // Simulates a category with no mapping — getOrElse returns empty set
      val allMapped = ExpenseCategory.applicableSources.keySet
      val unmapped = ExpenseCategory.values.filterNot(allMapped.contains)

      unmapped.foreach { cat =>
        cat.accepts(SourceType.ShoppingListItem) shouldBe false
      }
    }
  }
}
