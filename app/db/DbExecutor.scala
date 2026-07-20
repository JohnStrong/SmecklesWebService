package db

import slick.dbio.DBIO

import scala.concurrent.Future

/**
 * Database execution engine that decouples services from Slick's internal
 * connection management and transaction semantics.
 *
 * Repositories return uncommitted [[slick.dbio.DBIO]] actions (composable
 * database IO monads). Services compose multiple actions into a single
 * logical operation, then hand the composed action to this executor which
 * controls the transaction boundary and connection lifecycle.
 *
 * This pattern allows cross-table transactional consistency (e.g. updating
 * a shopping list item status and inserting an expense atomically) without
 * coupling repositories to each other or to execution concerns.
 *
 * @see [[docs/execution_engine/README.md]] for the full design rationale,
 *      alternatives considered, and decision summary.
 */
trait DbExecutor {

  /**
   * Executes a DBIO action without an explicit transaction wrapper.
   *
   * The action runs within a single database session. If the action itself
   * contains multiple statements, each executes independently — a failure
   * in a later statement does not roll back earlier ones.
   *
   * Use this for simple reads or single-statement writes where atomicity
   * across multiple operations is not required.
   *
   * @param action the DBIO action to execute
   * @tparam T the result type produced by the action
   * @return a Future containing the action's result
   */
  def run[T](action: DBIO[T]): Future[T]

  /**
   * Executes a DBIO action within a single database transaction.
   *
   * All statements within the action are committed atomically — if any
   * statement fails, the entire transaction is rolled back. Use this when
   * composing actions from multiple repositories that must succeed or fail
   * together (e.g. status update + expense insert).
   *
   * @param action the DBIO action to execute transactionally
   * @tparam T the result type produced by the action
   * @return a Future containing the action's result, or a failed Future
   *         if the transaction was rolled back
   */
  def runTransactionally[T](action: DBIO[T]): Future[T]
}
