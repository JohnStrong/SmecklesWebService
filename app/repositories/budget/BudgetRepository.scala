package repositories.budget

import models.Budget

import java.time.LocalDate
import scala.concurrent.Future
trait BudgetRepository {
  def get(email: String): Future[Either[String, List[Budget]]]
  def create(budget: Budget): Future[Either[String, Budget]]
  def update(email: String, periodStart: LocalDate, amountMinor: Long, currencyCode: String): Future[Either[String, Budget]]
  def delete(email: String, periodStart: LocalDate): Future[Either[String, Unit]]
}
