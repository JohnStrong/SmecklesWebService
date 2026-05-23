package org.myapps.shoppinglistservice.service

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.myapps.shoppinglistservice.model.{Item, ShoppingList}
import org.myapps.shoppinglistservice.repo.{ItemsRepository, ShoppingListRepository}
import java.util.Optional

class ShoppingCartServiceImplSpec extends AnyWordSpec with Matchers {

  private def createFixture() = {
    val mockShoppingListRepo = mock(classOf[ShoppingListRepository])
    val mockItemsRepo = mock(classOf[ItemsRepository])
    val service = new ShoppingCartServiceImpl(mockShoppingListRepo, mockItemsRepo)
    (service, mockShoppingListRepo, mockItemsRepo)
  }

  "ShoppingCartServiceImpl" should {

    "throw RuntimeException when shopping list not found" in {
      val (service, mockListRepo, _) = createFixture()
      when(mockListRepo.findById(1L)).thenReturn(Optional.empty())

      val ex = intercept[RuntimeException] {
        service.addItem(1L, "Milk")
      }
      ex.getMessage shouldBe "Shopping list not found"
    }

    "throw RuntimeException when item not found" in {
      val (service, mockListRepo, mockItemsRepo) = createFixture()
      val shoppingList = new ShoppingList()
      when(mockListRepo.findById(1L)).thenReturn(Optional.of(shoppingList))
      when(mockItemsRepo.findByName("Ghost")).thenReturn(Optional.empty())

      val ex = intercept[RuntimeException] {
        service.addItem(1L, "Ghost")
      }
      ex.getMessage shouldBe "Item not found. it must be out of stock..."
    }

    "save a new shopping list entry with quantity 1 when item exists" in {
      val (service, mockListRepo, mockItemsRepo) = createFixture()
      val shoppingList = new ShoppingList()
      val item = new Item()
      item.setName("Milk")
      when(mockListRepo.findById(1L)).thenReturn(Optional.of(shoppingList))
      when(mockItemsRepo.findByName("Milk")).thenReturn(Optional.of(item))

      service.addItem(1L, "Milk")

      verify(mockListRepo).save(argThat[ShoppingList] { entry =>
        entry.getItem == item && entry.getQuantity == 1
      })
    }
  }
}
