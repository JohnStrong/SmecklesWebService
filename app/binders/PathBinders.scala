package binders

import play.api.mvc.PathBindable
import java.time.LocalDate
import scala.util.Try

object PathBinders {
  implicit val localDatePathBindable: PathBindable[LocalDate] = new PathBindable[LocalDate] {
    def bind(key: String, value: String): Either[String, LocalDate] =
      Try(LocalDate.parse(value)).toEither.left.map(_ =>
        s"Invalid date format for '$key': expected yyyy-MM-dd, got '$value'"
      )

    def unbind(key: String, value: LocalDate): String = value.toString
  }
}
