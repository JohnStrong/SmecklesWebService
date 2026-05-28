package repositories.customer

import javax.inject.*
import play.api.db.slick.DatabaseConfigProvider
import repositories.SlickDataRepository
import models.Customer
import scala.concurrent.{ExecutionContext, Future}

class SlickCustomerRepository @Inject()(
     dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends SlickDataRepository[String, Customer](dbConfigProvider) {

  import profile.api.*

  // Implementation note: Slick works weird with case classes, so always define class for Table constructs
  private class CustomerTable(tag: Tag) extends Table[Customer](tag, "customers") {
    def email = column[String]("email", O.PrimaryKey)

    def * = email <> (Customer.apply, c => Some(c.email))
  }

  private val customers = TableQuery[CustomerTable]

  override def create(payload: Customer): Future[Either[String, Customer]] = {
    val action = customers.filter(_.email === payload.email)
      .forUpdate // SELECT ... FOR UPDATE =>  locks the row
      .result
      .headOption
      .flatMap {
        case Some(_) => DBIO.successful(Left(s"Customer with email ${payload.email} already exists."))
        case None => (customers += payload).map { _ => Right(payload) } // insert new
      }
      .transactionally // make a single transaction

    db.run(action)
  }
}
