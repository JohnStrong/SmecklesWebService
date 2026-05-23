package services

import scala.collection.mutable
import javax.inject._
import models.Customer

type ErrorMessage = String

trait CustomerService {
  def createCustomer(email: String): Either[ErrorMessage, Customer]
  def findByEmail(email: String): Either[ErrorMessage, Customer]
}

class CustomerServiceImpl @Inject()() extends CustomerService {

  private val customers = mutable.HashMap[String, Customer]()

  override def createCustomer(email: String): Either[ErrorMessage, Customer] = {
    customers.get(email)
      .toLeft { Customer(email) }
      .left.map(_ => s"Customer with email $email already exists.")
      .map { customer =>
        customers.put(email, customer)
        customer
      }
  }

  override def findByEmail(email: String): Either[ErrorMessage, Customer] = {
    customers.get(email)
      .toRight(s"Customer with email $email not found.")
  }
}
