package services

import javax.inject.*
import models.{Customer, User}
import repositories.DataRepository

import scala.concurrent.{ExecutionContext, Future}

type ErrorMessage = String

trait CustomerService {
  def createCustomer(email: String, authenticateUserEmail: String): Future[Either[ErrorMessage, Customer]]
  def findByEmail(email: String): Future[Either[ErrorMessage, Customer]]
  def deleteCustomer(email: String): Future[Either[ErrorMessage, Unit]]
}

class CustomerServiceImpl @Inject()(
    val customerDataRepository: DataRepository[String, Customer],
    val usersDataRepository: DataRepository[String, User]
)(implicit ec: ExecutionContext) extends CustomerService {

  override def createCustomer(email: String, authenticateUserEmail: String): Future[Either[ErrorMessage, Customer]] = {
    usersDataRepository.findByIdentifier(authenticateUserEmail).flatMap {
      // user doesn't yet exist, create a new user before adding any customers
      // TODO: check the error from findByIdentifier, it might be a transient issue not necessarily meaning the user doesn't exist
      case Left(_) => usersDataRepository.create(User(userId = None, email = authenticateUserEmail)).flatMap {
        case Left(errorMessage) => Future.successful(Left(errorMessage))
        case Right(user) => customerDataRepository.create(Customer(email, user.userId.get))
      }
      // user already exists, create the customer as normal
      case Right(user) => customerDataRepository.create(Customer(email, user.userId.get))
    }
  }

  override def findByEmail(email: String): Future[Either[ErrorMessage, Customer]] = {
    customerDataRepository.findByIdentifier(email)
  }

  override def deleteCustomer(email: String): Future[Either[ErrorMessage, Unit]] = {
    customerDataRepository.delete(email)
  }
}
