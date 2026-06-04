package services

import models.{ShoppingListItem, ShoppingListWithItems}
import repositories.DataRepository
import repositories.shoppinglist.ShoppingListRepository

import scala.concurrent.Future
import javax.inject.*

trait ShoppingListService {
  def getShoppingList(email: String): Future[Either[String, ShoppingListWithItems]]
  def create(email: String, name: String, items: List[ShoppingListItem]): Future[Either[String, ShoppingListWithItems]]
}

class ShoppingListServiceImpl @Inject()(
    shoppingListRepository: DataRepository[String, ShoppingListWithItems]
) extends ShoppingListService {

  override def getShoppingList(email: String): Future[Either[String, ShoppingListWithItems]] = {
    shoppingListRepository.findByIdentifier(email)
  }

  override def create(email: String, name: String, items: List[ShoppingListItem]): Future[Either[String, ShoppingListWithItems]] = {
    shoppingListRepository.create(ShoppingListWithItems(email, name, items))
  }
}
