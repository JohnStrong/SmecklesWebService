package repositories

import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

import scala.concurrent.{ExecutionContext, Future}

abstract class SlickDataRepository[KEY, ENTITY](
   protected val dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext)
  extends DataRepository[KEY, ENTITY]
  with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api.*

  override def findByIdentifier(id: KEY): Future[Either[String, ENTITY]] = ???

  override def create(payload: ENTITY): Future[Either[String, ENTITY]] = ???

  override def delete(id: KEY): Future[Either[String, Unit]] = ???

  override def findAllByIdentifier(id: KEY): Future[Either[String, List[ENTITY]]] = ???
}
