package repositories.budget

import models.Budget
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import java.time.LocalDate
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class CustomerBudgetRow(
    id: Long,
    email: String,
    periodStart: LocalDate,
    periodEnd: LocalDate,
    amountMinor: Long,
    currencyCode: String
)

object CustomerBudgetRow {
  def toBudget(customerBudgetRow: CustomerBudgetRow): Budget = {
    Budget(
      email = customerBudgetRow.email,
      periodStart = customerBudgetRow.periodStart,
      periodEnd = customerBudgetRow.periodEnd,
      amountMinor = customerBudgetRow.amountMinor,
      currencyCode = customerBudgetRow.currencyCode
    )
  }
}

class SlickBudgetRepository @Inject()(
   val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends BudgetRepository
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  private class CustomerBudgetsTable(tag: Tag) extends Table[CustomerBudgetRow](tag, "customer_budgets") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")
    def periodStart = column[LocalDate]("period_start")
    def periodEnd = column[LocalDate]("period_end")
    def amountMinor = column[Long]("amount_minor")
    def currencyCode = column[String]("currency_code")

    def *  = (id, email, periodStart, periodEnd, amountMinor, currencyCode) <> (CustomerBudgetRow.apply, CustomerBudgetRow.unapply)
  }
  private val customerBudgets = TableQuery[CustomerBudgetsTable]

  override def get(email: String): Future[Either[String, List[Budget]]] = {
    val action = for {
      budgets <- filterByEmail(email).result
    } yield Right(budgets.map(row => CustomerBudgetRow.toBudget(row)).toList)
    db.run(action)
  }

  override def create(budget: Budget): Future[Either[String, Budget]] = {
    val action = (for {
      overlapping <- filterByEmailAndRange(budget.email, budget.periodStart, budget.periodEnd)
        .forUpdate
        .result
        .headOption
      result <- overlapping match {
        case Some(existing) =>
          DBIO.successful(Left(s"Budget period overlaps with an existing budget (${existing.periodStart} to ${existing.periodEnd})"))
        case None =>
          for {
            _ <- customerBudgets += CustomerBudgetRow(
              0L,
              budget.email,
              budget.periodStart,
              budget.periodEnd,
              budget.amountMinor,
              budget.currencyCode
            )
          } yield Right(budget)
      }
    } yield result).transactionally

    db.run(action)
  }

  private def filterByEmail(email: String) =
    customerBudgets.filter(cb => cb.email === email)

  private def filterByEmailAndRange(email: String, periodStart: LocalDate, periodEnd: LocalDate) =
    customerBudgets.filter(cb =>
      cb.email === email &&
      cb.periodStart < periodEnd &&
      cb.periodEnd > periodStart)

  override def update(budget: Budget): Future[Either[String, Budget]] = ???

  override def delete(email: String, periodStart: LocalDate): Unit = ???

}
