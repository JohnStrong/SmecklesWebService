package repositories.shoppinglist

import models.{DecoupledShoppingList, DecoupledShoppingListItem}
import play.api.db.slick.DatabaseConfigProvider
import repositories.SlickDataRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SlickShoppingListRepository @Inject()(
   dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends SlickDataRepository[String, DecoupledShoppingList](dbConfigProvider) {

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

  override def create(payload: DecoupledShoppingList): Future[Either[String, DecoupledShoppingList]] = ???

  override def findByIdentifier(id: String): Future[Either[String, DecoupledShoppingList]] = ???

}
