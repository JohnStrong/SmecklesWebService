package repositories.user

import javax.inject.*
import play.api.db.slick.DatabaseConfigProvider
import repositories.SlickDataRepository
import models.User
import scala.concurrent.{ExecutionContext, Future}

class SlickUserRepository @Inject()(
  dbConfigProvider: DatabaseConfigProvider
)(implicit ec: ExecutionContext) extends SlickDataRepository[String, User](dbConfigProvider) {

  import profile.api.*

  private class UserTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
    def email = column[String]("email")

    def * = (id.?, email) <> ((User.apply _).tupled, u => Some((u.userId, u.email)))
  }

  private val users = TableQuery[UserTable]

  override def create(payload: User): Future[Either[String, User]] = {
    val action = users.filter(_.email === payload.email)
      .result
      .headOption
      .flatMap {
        case Some(existing) => DBIO.successful(Left(s"User with email ${payload.email} already exists."))
        case None =>
          ((users returning users.map(_.id) into ((user, id) => user.copy(userId = Some(id)))) += payload)
            .map(created => Right(created))
      }
      .transactionally

    db.run(action)
  }

  override def findByIdentifier(email: String): Future[Either[String, User]] = {
    val action = users.filter(_.email === email)
      .result
      .headOption
      .map {
        case Some(user) => Right(user)
        case None => Left(s"User with email '$email' not found.")
      }
    db.run(action)
  }

  override def findAllByIdentifier(id: String): Future[Either[String, List[User]]] = ???
}
