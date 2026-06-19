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
    def userId = column[Long]("user_id")

    def * = (email, userId) <> (Customer.apply, c => Some((c.email, c.userId)))
  }

  private val customers = TableQuery[CustomerTable]

  override def create(payload: Customer): Future[Either[String, Customer]] = {
    val action = customers.filter(_.email === payload.email)
      .forUpdate // SELECT ... FOR UPDATE =>  locks the row (pessimistic locking)
      .result
      .headOption
      .flatMap {
        case Some(_) => DBIO.successful(Left(s"Customer with email ${payload.email} already exists."))
        case None => (customers += payload).map { _ => Right(payload) } // insert new
      }
      .transactionally // make a single transaction

    db.run(action)
  }

  override def findByIdentifier(id: String): Future[Either[String, Customer]] = {
    val action = customers.filter(_.email === id)
      // no locking - eventually consistent
      .result
      /*
        headOption gives you a DBIO[Option[Customer]]
       */
      .headOption
      /*
        map for transforming DBIO[Option[Customer]] to DBIO[Either] as per the interface
       */
      .map {
        case Some(customer) => Right(customer)
        case None => Left(s"Customer with email '$id' not found.")
      }
    db.run(action)
  }

  override def delete(id: String): Future[Either[String, Unit]] = ???

  override def findAllByIdentifier(id: String): Future[Either[String, List[Customer]]] = ???
}
