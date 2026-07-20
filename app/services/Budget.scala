package services

import models.Budget
import repositories.budget.BudgetRepository

import java.time.LocalDate
import javax.inject.*
import scala.concurrent.Future

trait BudgetService {
  def getBudgets(email: String): Future[Either[String, List[Budget]]]
  def create(budget: Budget): Future[Either[String, Budget]]
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
}
