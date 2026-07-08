package repositories.shoppinglist

import models.ShoppingListWithItems

import scala.concurrent.Future

trait ShoppingListRepository {
  def create(payload: ShoppingListWithItems): Future[Either[String, ShoppingListWithItems]]

  def findByEmail(email: String): Future[Either[String, ShoppingListWithItems]]

  def findAllByEmail(email: String): Future[Either[String, List[ShoppingListWithItems]]]

  def deleteByEmailAndName(email: String, name: String): Future[Either[String, Unit]]
}
