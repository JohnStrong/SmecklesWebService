package repositories.shoppinglist

import models.{ShoppingListItem, ShoppingListWithItems}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import repositories.SlickDataRepository
import slick.jdbc.JdbcProfile
import slick.lifted.Query

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class DecoupledShoppingList(
  id: Long,
  email: String,
  name: String,
  periodStart: LocalDate,
  dayDate: LocalDate
)

case class DecoupledShoppingListItem(
  id: Long,
  shoppingListId: Long,
  name: String,
  quantity: Int,
  currencyCode: String,
  unitAmountMinor: Long,
  lineAmountMinor: Long
)

object DecoupledShoppingList {
  def toShoppingListWithItems(shoppingList: DecoupledShoppingList, items: Seq[DecoupledShoppingListItem]) =
    ShoppingListWithItems(
      email = shoppingList.email,
      name = shoppingList.name,
      periodStart = shoppingList.periodStart,
      dayDate = shoppingList.dayDate,
      items = items.map(i => ShoppingListItem(
        name = i.name,
        quantity = i.quantity,
        currencyCode = i.currencyCode,
        unitAmountMinor = i.unitAmountMinor,
        lineAmountMinor = i.lineAmountMinor
      )).toList
    )
}

class SlickShoppingListRepository @Inject()(
   protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends ShoppingListRepository
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  private class ShoppingListsTable(tag: Tag) extends Table[DecoupledShoppingList](tag, "shopping_lists") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def name = column[String]("name")
    def periodStart = column[LocalDate]("period_start")
    def dayDate = column[LocalDate]("day_date")

    def * = (id, email, name, periodStart, dayDate) <> (DecoupledShoppingList.apply, DecoupledShoppingList.unapply)
  }
  private val shoppingLists = TableQuery[ShoppingListsTable]

  private class ShoppingListItemsTable(tag: Tag) extends Table[DecoupledShoppingListItem](tag, "shopping_list_items") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def shoppingListId = column[Long]("shopping_list_id")
    def name = column[String]("name")
    def quantity = column[Int]("quantity")
    def currencyCode = column[String]("currency_code")
    def unitAmountMinor = column[Long]("unit_amount_minor")
    def lineAmountMinor = column[Long]("line_amount_minor")

    def * = (id, shoppingListId, name, quantity, currencyCode, unitAmountMinor, lineAmountMinor) <> (DecoupledShoppingListItem.apply, DecoupledShoppingListItem.unapply)

    def shoppingListFK = foreignKey("fk_list", shoppingListId, shoppingLists)(_.id, onDelete = ForeignKeyAction.Cascade)
  }
  private val shoppingListItems = TableQuery[ShoppingListItemsTable]

  override def create(payload: ShoppingListWithItems): Future[Either[String, ShoppingListWithItems]] = {
    val action = (for {
      existing <- emailDayAndNameFilter(payload.email, payload.dayDate, payload.name)
        .forUpdate
        .result
        .headOption
      result <- existing match {
        case Some(_) => DBIO.successful(Left(s"Shopping list '${payload.name}' already exists on ${payload.dayDate}."))
        case None =>
          for {
            listId <- insertShoppingList(payload.email, payload.name, payload.periodStart, payload.dayDate)
            _ <- insertShoppingListItems(listId, payload.items)
          } yield Right(payload)
      }
    } yield result).transactionally

    db.run(action)
  }

  override def findByEmail(email: String): Future[Either[String, ShoppingListWithItems]] = {
    val action = (for {
      shoppingList <- emailFilter(email).result.headOption
      result <- shoppingList match {
        case Some(list) => for {
          items <- findItemsByIdentifier(list.id)
        } yield Right(DecoupledShoppingList.toShoppingListWithItems(list, items))
        case None => DBIO.successful(Left(s"No shopping list found for email $email."))
      }
    } yield result)

    db.run(action)
  }

  override def findAllByEmail(email: String): Future[Either[String, List[ShoppingListWithItems]]] = {
    val action = for {
      shoppingLists <- emailFilter(email).result
      result <- if (shoppingLists.isEmpty) DBIO.successful(Right(List.empty[ShoppingListWithItems]))
        else {
          val withItems = shoppingLists.map { list =>
            findItemsByIdentifier(list.id)
              .map(items => DecoupledShoppingList.toShoppingListWithItems(list, items))
          }
          DBIO.sequence(withItems).map(results => Right(results.to(List)))
        }
    } yield result

    db.run(action)
  }

  override def deleteByEmailAndName(email: String, name: String): Future[Either[String, Unit]] = {
    val action = shoppingLists
      .filter(sl => sl.email === email && sl.name === name)
      .delete
      .map(_ => Right(()))
    db.run(action).recover {
      case ex => Left(s"Failed to delete shopping list '$name' for customer $email: ${ex.getMessage}")
    }
  }

  private def emailFilter(email: String) = shoppingLists.filter(_.email === email)

  private def emailDayAndNameFilter(email: String, dayDate: LocalDate, name: String) =
    shoppingLists.filter(sl => sl.email === email && sl.dayDate === dayDate && sl.name === name)

  private def findItemsByIdentifier(id: Long) = shoppingListItems.filter(_.shoppingListId === id).result

  private def insertShoppingList(email: String, name: String, periodStart: LocalDate, dayDate: LocalDate) =
    (shoppingLists.map(sl => (sl.email, sl.name, sl.periodStart, sl.dayDate)) returning shoppingLists.map(_.id)) += (email, name, periodStart, dayDate)

  private def insertShoppingListItems(shoppingListId: Long, items: List[ShoppingListItem]) = {
    shoppingListItems ++= items.map(i => DecoupledShoppingListItem(
      0L,
      shoppingListId,
      i.name,
      i.quantity,
      i.currencyCode,
      i.unitAmountMinor,
      i.lineAmountMinor
    ))
  }
}
