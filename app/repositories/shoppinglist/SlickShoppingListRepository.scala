package repositories.shoppinglist

import models.{ShoppingListWithItems, ShoppingListItem}
import play.api.db.slick.DatabaseConfigProvider
import repositories.SlickDataRepository
import slick.lifted.Query

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class DecoupledShoppingList(id: Long, email: String, name: String)
case class DecoupledShoppingListItem(
  id: Long, // item id
  shoppingListId: Long, // id of the shopping list this item belongs to
  name: String, // the item name
  quantity: Int // amount of the item
)

object DecoupledShoppingList {
  def toShoppingListWithItems(shoppingList: DecoupledShoppingList, items: Seq[DecoupledShoppingListItem]) =
    ShoppingListWithItems(
      id = Some(shoppingList.id),
      email = shoppingList.email,
      name = shoppingList.name,
      items = items.map(i => ShoppingListItem(name = i.name, quantity = i.quantity)).toList
    )
}

class SlickShoppingListRepository @Inject()(
   dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends SlickDataRepository[Long, ShoppingListWithItems](dbConfigProvider) {

  import profile.api.*

  // 1:1 with customer for now
  // Implementation note: Slick works weird with case classes, so always define class for Table constructs
  private class ShoppingListsTable(tag: Tag) extends Table[DecoupledShoppingList](tag, "shopping_lists") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc) // Note. use slick 'returning' during insert to get the id back
    def email = column[String]("email")
    def name = column[String]("name")

    def * = (id, email, name) <> (DecoupledShoppingList.apply, DecoupledShoppingList.unapply)
  }
  private val shoppingLists = TableQuery[ShoppingListsTable]

  // TODO: Implement https://github.com/JohnStrong/ShoppingListWebService/issues/2
  private class ShoppingListItemsTable(tag: Tag) extends Table[DecoupledShoppingListItem](tag, "shopping_list_items") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc) // Note. use slick 'returning' during insert to get the id back
    def shoppingListId = column[Long]("shopping_list_id")
    def name = column[String]("name")
    def quantity = column[Int]("quantity")

    def * = (id, shoppingListId, name, quantity) <> (DecoupledShoppingListItem.apply, DecoupledShoppingListItem.unapply)

    def shoppingListFK = foreignKey("fk_list", shoppingListId, shoppingLists)(_.id, onDelete = ForeignKeyAction.Cascade)
  }
  private val shoppingListItems = TableQuery[ShoppingListItemsTable]

  override def create(payload: ShoppingListWithItems): Future[Either[String, ShoppingListWithItems]] = {
    val action = (for {
      existing <- findByEmail(payload.email)
        .forUpdate  // lock this row (or the gap where it would be) until this transaction commits... (ensure no duplicate entries)
        .result
        .headOption
      result <- existing match {
        case Some(_) => DBIO.successful(Left(s"Shopping list already exists for email ${payload.email}."))
        case None =>
          for {
            listId <- insertShoppingList(payload.email, payload.name)
            _ <- insertShoppingListItems(listId, payload.items)
            // yield transforms to .map so... DBIO[A,B].map { a:A => Right(b:B) }
          } yield Right(payload.copy(id = Some(listId)))
      }
    } yield result).transactionally

    // THIS kicks off the db transaction 'action' above described by the for-comprehension monadically
    db.run(action)
  }

  override def findByIdentifier(id: Long): Future[Either[String, ShoppingListWithItems]] = {
    val action = (for {
      shoppingList <- shoppingLists.filter(sl => sl.id === id).result.headOption
      result <- shoppingList match {
        case Some (list) => for {
          items <- findItemsByIdentifier(id)
        } yield Right(DecoupledShoppingList.toShoppingListWithItems(list, items))
        case None => DBIO.successful(Left(s"No shopping list found for email $id."))
      }
    } yield result)

    db.run(action)
  }

  private def findByEmail(email: String) = shoppingLists.filter(_.email === email)

  private def findItemsByIdentifier(id: Long) = shoppingListItems.filter(_.shoppingListId === id).result

  // effectively: INSERT INTO shopping_lists (email, name) VALUES ('user@example.com', 'Groceries') RETURNING id;
  private def insertShoppingList(email: String, name: String) =
    (shoppingLists.map(sl => (sl.email, sl.name)) returning shoppingLists.map(_.id)) += (email, name)

  /*
  effectively:
    INSERT INTO shopping_list_items (shopping_list_id, name, quantity)
    VALUES
      (42, 'Milk', 2),
      (42, 'Bread', 1),
      ...;
   */
  private def insertShoppingListItems(shoppingListId: Long, items: List[ShoppingListItem]) = {
    shoppingListItems ++= items.map(i => DecoupledShoppingListItem(0L, shoppingListId, i.name, i.quantity))
  }
}
