package services

import java.util.UUID.randomUUID
import scala.collection.mutable
import javax.inject._
import models.Customer

type CustomerEmail = String
type CustomerId = String
type ErrorMessage = String // for now

trait CustomerService {
  def createCustomer(email: String): Either[ErrorMessage, Customer]
  def findById(id: String): Either[ErrorMessage, Customer]
}

class CustomerServiceImpl @Inject()() extends CustomerService {

  // temporary in-memory db
  private val customersByEmail = mutable.HashMap[CustomerEmail, Customer]()
  private val customersById = mutable.HashMap[CustomerId, Customer]()

  override def createCustomer(email: String): Either[ErrorMessage, Customer] = {
    customersByEmail.get(email)
      .toLeft { insert(email) } // if no customer found in map - we can create an entry
      .left.map(error => s"Customer with email $email already exists.") // if customer found, we return ErrorMessage
  }

  override def findById(id: String): Either[ErrorMessage, Customer] = {
    customersById.get(id)
      .toRight(s"Customer with id $id not found.")
  }

  private def insert(email: String): Customer = {
    val id = randomUUID()
    val customer = Customer(id = id, email = email)
    customersByEmail.put(email, customer)
    customersById.put(id.toString, customer)
    customer
  }
}
