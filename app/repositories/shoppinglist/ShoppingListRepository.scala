package repositories.shoppinglist

import models.{ShoppingListItem, ShoppingListWithItems}
import slick.dbio.DBIO

import java.time.LocalDate
import scala.concurrent.Future

trait ShoppingListRepository {
  def create(payload: ShoppingListWithItems): Future[Either[String, ShoppingListWithItems]]

  def findByEmail(email: String): Future[Either[String, ShoppingListWithItems]]

  def findAllByEmail(email: String): Future[Either[String, List[ShoppingListWithItems]]]

  def deleteByEmailNameAndDay(email: String, name: String, dayDate: LocalDate): Future[Either[String, Unit]]

  @Deprecated("Use 'updateItemStatusAction' instead")
  def updateItemStatus(email: String, listName: String, itemName: String, status: String): Future[Either[String, ShoppingListItem]]

  def updateItemStatusAction(email: String, listName: String, itemName: String, status: String): DBIO[Either[String, ShoppingListItem]]
}
