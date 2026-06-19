package repositories.shoppinglist

import models.ShoppingList
import repositories.InMemoryDataRepository

import scala.concurrent.Future

class ShoppingListRepository extends InMemoryDataRepository[String, ShoppingList] {

  /**
   * Persist a new entity in the backing store.
   *
   * @param payload the entity to persist
   * @return a Future containing Right(entity) on success, or Left(errorMessage)
   *         if the entity already exists or persistence fails
   */
  override def create(payload: ShoppingList): Future[Either[String, ShoppingList]] = {
    Future.successful {
      repo.get(payload.email)
      .toLeft {
        repo.put(payload.email, payload)
        payload
      }
      .left.map { _ => s"Shopping list already exists with name ${payload.name}" }
    }
  }

  /**
   * Look up an entity by its unique identifier.
   *
   * @param id the unique identifier to search by (customer email)
   * @return a Future containing Right(entity) if found, or Left(errorMessage) if not
   */
  override def findByIdentifier(id: String): Future[Either[String, ShoppingList]] = {
    Future.successful {
      repo.get(id).toRight(s"No shopping list found for email $id.")
    }
  }

  override def delete(id: String): Future[Either[String, Unit]] = ???

  override def findAllByIdentifier(id: String): Future[Either[String, List[ShoppingList]]] = ???
}
