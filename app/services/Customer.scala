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
    findOrCreateUser(authenticateUserEmail).flatMap {
      case Left(error) => Future.successful(Left(error))
      case Right(user) => customerDataRepository.create(Customer(email, user.userId.get))
    }
  }

  private def findOrCreateUser(email: String): Future[Either[ErrorMessage, User]] = {
    usersDataRepository.create(User(userId = None, email = email)).flatMap {
      case Right(user) => Future.successful(Right(user))
      case Left(error) if error.contains("already exists") => usersDataRepository.findByIdentifier(email)
      case Left(error) => Future.successful(Left(error))
    }
  }

  override def findByEmail(email: String): Future[Either[ErrorMessage, Customer]] = {
    customerDataRepository.findByIdentifier(email)
  }

  override def deleteCustomer(email: String): Future[Either[ErrorMessage, Unit]] = {
    customerDataRepository.delete(email)
  }
}
