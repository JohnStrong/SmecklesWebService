package services

import models.{ ShoppingListItem, ShoppingList }
import scala.collection.mutable

trait ShoppingListService {
  def getShoppingList(email: String): Either[String, ShoppingList]
  def create(email: String, name: String, items: List[ShoppingListItem]): Either[String, ShoppingList]
}

class ShoppingListServiceImpl extends ShoppingListService {

  // TODO: move to a db - h2 initially for development
  private val shoppingLists = mutable.HashMap[String, ShoppingList]()

  override def getShoppingList(email: String): Either[String, ShoppingList] = {
    shoppingLists.get(email)
      .toRight(s"No shopping list found for email $email.")
  }

  override def create(email: String, name: String, items: List[ShoppingListItem]): Either[String, ShoppingList] = {
    // TODO: add checks for the items ensuring it is not empty or items have non-falsey value for quantity and name
    shoppingLists.get(email)
      .toLeft { ShoppingList(name, items) }
      .left.map { _ => s"Shopping list already exists for email $email" }
      .map { shoppingList => shoppingLists.put(email, shoppingList); shoppingList }
  }
}
