package repositories.customer

import repositories.InMemoryDataRepository
import models.Customer

import scala.concurrent.Future

/**
 * In-memory repository for [[models.Customer]] entities.
 *
 * Extends [[InMemoryDataRepository]] which provides the backing `repo` HashMap
 * keyed by email (String). Uses [[Future.successful]] since all operations are
 * synchronous map lookups — no thread pool submission or blocking occurs.
 *
 * This implementation is suitable for local development and testing. For
 * production use, swap to a Slick/database-backed implementation via Guice
 * bindings in [[Module]].
 */
class CustomerRepository extends InMemoryDataRepository[String, Customer] {

  override def create(payload: Customer): Future[Either[String, Customer]] = {
    Future.successful {
      repo.get(payload.email)
        .toLeft { payload }
        .left.map(_ => s"Customer with email ${payload.email} already exists.")
        .map { customer =>
          repo.put(payload.email, customer)
          customer
        }
    }
  }
  override def findByIdentifier(id: String): Future[Either[String, Customer]] = {
    Future.successful {
      repo.get(id)
        .toRight(s"Customer with email '$id' not found.")
    }
  }

  override def delete(id: String): Future[Either[String, Unit]] = ???

  override def findAllByIdentifier(id: String): Future[Either[String, List[Customer]]] = ???
}
