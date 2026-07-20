package services

import models.Budget
import repositories.budget.BudgetRepository

import java.time.LocalDate
import javax.inject.*
import scala.concurrent.Future

trait BudgetService {
  def getBudgets(email: String): Future[Either[String, List[Budget]]]
  def create(budget: Budget): Future[Either[String, Budget]]
  def update(email: String, periodStart: LocalDate, amountMinor: Long, currencyCode: String): Future[Either[String, Budget]]
  def delete(email: String, periodStart: LocalDate): Future[Either[String, Unit]]
}

class BudgetServiceImpl @Inject()(
  budgetRepository: BudgetRepository
) extends BudgetService {

  override def getBudgets(email: String): Future[Either[String, List[Budget]]] = {
    budgetRepository.get(email)
  }

  override def create(budget: Budget): Future[Either[String, Budget]] = {
    budgetRepository.create(budget)
  }

  override def update(email: String, periodStart: LocalDate, amountMinor: Long, currencyCode: String): Future[Either[String, Budget]] = {
    budgetRepository.update(email, periodStart, amountMinor, currencyCode)
  }

  override def delete(email: String, periodStart: LocalDate): Future[Either[String, Unit]] = {
    budgetRepository.delete(email, periodStart)
  }
}
