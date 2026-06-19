package repositories

import scala.concurrent.Future

/**
 * Base repository trait defining the contract for data persistence operations.
 *
 * All implementations return [[scala.concurrent.Future]] to support non-blocking,
 * asynchronous execution — whether backed by an in-memory store, H2 (dev), or
 * PostgreSQL (production). The [[Either]] encodes success (Right) or a domain
 * error message (Left) without throwing exceptions.
 *
 * @tparam IDENTIFIER the type used to uniquely look up an entity (e.g. String email)
 * @tparam ENTITY the domain model type stored in this repository
 */
trait DataRepository[IDENTIFIER, ENTITY] {

  /**
   * Persist a new entity in the backing store.
   *
   * @param payload the entity to persist
   * @return a Future containing Right(entity) on success, or Left(errorMessage)
   *         if the entity already exists or persistence fails
   */
  def create(payload: ENTITY): Future[Either[String, ENTITY]]

  /**
   * Look up an entity by its unique identifier.
   *
   * @param id the unique identifier to search by
   * @return a Future containing Right(entity) if found, or Left(errorMessage) if not
   */
  def findByIdentifier(id: IDENTIFIER): Future[Either[String, ENTITY]]

  /**
   * Look up all entities by some column identifier/filter.
   *
   * @param id the identifier to search by
   * @return a Future containing Right(list(entity)) if found, or Left(errorMessage) if not
   */
  def findAllByIdentifier(id: IDENTIFIER): Future[Either[String, List[ENTITY]]]

  /**
   * Delete a resource/entity by its identifier
   *
   * @param id the unique identifier of the resource to delete
   * @return a Future containing Right(()) on success, or Left(errorMessage) if deletion fails
   */
  def delete(id: IDENTIFIER): Future[Either[String, Unit]]
}
