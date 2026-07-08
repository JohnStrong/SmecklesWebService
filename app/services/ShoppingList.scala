package services

import models.{ShoppingListItem, ShoppingListWithItems}
import repositories.DataRepository
import repositories.shoppinglist.ShoppingListRepository

import scala.concurrent.Future
import javax.inject.*

trait ShoppingListService {
  @Deprecated("use getShoppingLists instead")
  def getShoppingList(email: String): Future[Either[String, ShoppingListWithItems]]

  def getShoppingLists(email: String): Future[Either[String, List[ShoppingListWithItems]]]

  def create(email: String, name: String, items: List[ShoppingListItem]): Future[Either[String, ShoppingListWithItems]]

  def delete(email: String, name: String): Future[Either[String, Unit]]
}

class ShoppingListServiceImpl @Inject()(
    shoppingListRepository: DataRepository[String, ShoppingListWithItems]
) extends ShoppingListService {

  override def getShoppingList(email: String): Future[Either[String, ShoppingListWithItems]] = {
    shoppingListRepository.findByIdentifier(email)
  }

  override def getShoppingLists(email: String): Future[Either[String, List[ShoppingListWithItems]]] = {
    shoppingListRepository.findAllByIdentifier(email)
  }

  override def create(email: String, name: String, items: List[ShoppingListItem]): Future[Either[String, ShoppingListWithItems]] = {
    shoppingListRepository.create(ShoppingListWithItems(email, name, items))
  }

  override def delete(email: String, name: String): Future[Either[String, Unit]] = ???
}
