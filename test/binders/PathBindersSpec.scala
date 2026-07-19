package binders

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.EitherValues
import java.time.LocalDate

class PathBindersSpec extends AnyWordSpec with Matchers with EitherValues {

  private val binder = PathBinders.localDatePathBindable

  "LocalDate PathBindable" should {

    "bind a valid yyyy-MM-dd date string to LocalDate" in {
      binder.bind("dayDate", "2026-07-05").value shouldBe LocalDate.of(2026, 7, 5)
    }

    "bind the first day of a month" in {
      binder.bind("dayDate", "2026-01-01").value shouldBe LocalDate.of(2026, 1, 1)
    }

    "bind the last day of a month" in {
      binder.bind("dayDate", "2026-07-31").value shouldBe LocalDate.of(2026, 7, 31)
    }

    "return Left for an invalid date string" in {
      val result = binder.bind("dayDate", "banana")
      result.left.value should include("Invalid date format")
      result.left.value should include("banana")
    }

    "return Left for a date with wrong separator" in {
      binder.bind("dayDate", "2026/07/05").left.value should include("Invalid date format")
    }

    "return Left for an incomplete date" in {
      binder.bind("dayDate", "2026-07").left.value should include("Invalid date format")
    }

    "return Left for an invalid day (e.g. Feb 30)" in {
      binder.bind("dayDate", "2026-02-30").left.value should include("Invalid date format")
    }

    "return Left for an empty string" in {
      binder.bind("dayDate", "").left.value should include("Invalid date format")
    }

    "include the key name in the error message" in {
      binder.bind("myParam", "not-a-date").left.value should include("myParam")
    }

    "unbind a LocalDate to yyyy-MM-dd string" in {
      binder.unbind("dayDate", LocalDate.of(2026, 7, 5)) shouldBe "2026-07-05"
    }
  }
}
