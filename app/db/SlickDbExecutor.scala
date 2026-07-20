package db

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * Slick-backed implementation of [[DbExecutor]].
 *
 * Delegates action execution to the underlying Slick database session managed
 * by play-slick's [[DatabaseConfigProvider]]. Mixes in [[HasDatabaseConfigProvider]]
 * to obtain the configured `JdbcProfile` and its associated connection pool
 * (HikariCP by default in Play).
 *
 * This is the single point in the application where DBIO actions are materialised
 * into database calls. All connection acquisition, statement execution, and
 * connection release happen here — repositories and services never interact with
 * the connection pool directly.
 *
 * @param dbConfigProvider Play-Slick's database configuration provider, injected by
 *                         Guice. Holds the JDBC profile, connection pool settings, and
 *                         driver configuration defined in `application.conf` under
 *                         `slick.dbs.default`.
 * @param ec implicit ExecutionContext for mapping over Futures returned by `db.run`.
 */
class SlickDbExecutor @Inject()(
  val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends DbExecutor
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  override def run[T](action: DBIO[T]): Future[T] = db.run(action)

  override def runTransactionally[T](action: DBIO[T]): Future[T] = run(action.transactionally)
}
