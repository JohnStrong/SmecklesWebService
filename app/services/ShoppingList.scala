package services

import models.{ShoppingListItem, ShoppingListWithItems}
import repositories.shoppinglist.ShoppingListRepository

import scala.concurrent.Future
import javax.inject.*

trait ShoppingListService {
  @Deprecated("use getShoppingLists instead")
  def getShoppingList(email: String): Future[Either[String, ShoppingListWithItems]]

  def getShoppingLists(email: String): Future[Either[String, List[ShoppingListWithItems]]]

  def create(shoppingListWithItems: ShoppingListWithItems): Future[Either[String, ShoppingListWithItems]]

  def delete(email: String, name: String): Future[Either[String, Unit]]

  def updateItemStatus(email: String, listName: String, itemName: String, status: String): Future[Either[String, ShoppingListItem]]
}

class ShoppingListServiceImpl @Inject()(
    shoppingListRepository: ShoppingListRepository
) extends ShoppingListService {

  private val validStatuses = Set("pending", "completed")

  override def getShoppingList(email: String): Future[Either[String, ShoppingListWithItems]] = {
    shoppingListRepository.findByEmail(email)
  }

  override def getShoppingLists(email: String): Future[Either[String, List[ShoppingListWithItems]]] = {
    shoppingListRepository.findAllByEmail(email)
  }

  override def create(shoppingListWithItems: ShoppingListWithItems): Future[Either[String, ShoppingListWithItems]] = {
    shoppingListRepository.create(shoppingListWithItems)
  }

  override def delete(email: String, name: String): Future[Either[String, Unit]] = {
    shoppingListRepository.deleteByEmailAndName(email, name)
  }

  override def updateItemStatus(email: String, listName: String, itemName: String, status: String): Future[Either[String, ShoppingListItem]] = {
    if (!validStatuses.contains(status))
      Future.successful(Left("Invalid status value"))
    else
      shoppingListRepository.updateItemStatus(email, listName, itemName, status)
  }
}
