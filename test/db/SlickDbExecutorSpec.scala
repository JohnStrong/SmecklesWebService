package db

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import slick.dbio.DBIO
import slick.jdbc.H2Profile.api.*

import scala.concurrent.ExecutionContext

class SlickDbExecutorSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with GuiceOneAppPerTest {

  implicit private val ec: ExecutionContext = ExecutionContext.global

  implicit val patience: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  private def executor = app.injector.instanceOf[DbExecutor]

  "run" should {

    "execute a simple DBIO action and return the result" in {
      val action = DBIO.successful(42)

      val result = executor.run(action).futureValue

      result shouldBe 42
    }

    "execute a SQL query action against the database" in {
      val action = sql"SELECT 1".as[Int].head

      val result = executor.run(action).futureValue

      result shouldBe 1
    }

    "return a failed Future when the action fails" in {
      val action = DBIO.failed(new RuntimeException("deliberate failure"))

      val result = executor.run(action).failed.futureValue

      result.getMessage shouldBe "deliberate failure"
    }

    "execute multiple independent actions sequentially" in {
      val action = for {
        a <- DBIO.successful(10)
        b <- DBIO.successful(20)
      } yield a + b

      val result = executor.run(action).futureValue

      result shouldBe 30
    }
  }

  "runTransactionally" should {

    "execute a simple DBIO action and return the result" in {
      val action = DBIO.successful("hello")

      val result = executor.runTransactionally(action).futureValue

      result shouldBe "hello"
    }

    "execute a SQL query within a transaction" in {
      val action = sql"SELECT 1 + 1".as[Int].head

      val result = executor.runTransactionally(action).futureValue

      result shouldBe 2
    }

    "roll back all statements when a later action fails" in {
      // Create a temp table, insert a row, then fail — row should not persist
      val setup = sqlu"CREATE TABLE IF NOT EXISTS txn_test (amount INT NOT NULL)"
      executor.run(setup).futureValue

      val action = for {
        _ <- sqlu"INSERT INTO txn_test (amount) VALUES (999)"
        _ <- DBIO.failed(new RuntimeException("forced rollback"))
      } yield ()

      executor.runTransactionally(action).failed.futureValue

      // Verify the insert was rolled back
      val count = executor.run(sql"SELECT COUNT(*) FROM txn_test WHERE amount = 999".as[Int].head).futureValue
      count shouldBe 0

      // Cleanup
      executor.run(sqlu"DROP TABLE txn_test").futureValue
    }

    "commit all statements when action succeeds" in {
      val setup = sqlu"CREATE TABLE IF NOT EXISTS txn_test_success (amount INT NOT NULL)"
      executor.run(setup).futureValue

      val action = for {
        _ <- sqlu"INSERT INTO txn_test_success (amount) VALUES (1)"
        _ <- sqlu"INSERT INTO txn_test_success (amount) VALUES (2)"
      } yield ()

      executor.runTransactionally(action).futureValue

      val count = executor.run(sql"SELECT COUNT(*) FROM txn_test_success".as[Int].head).futureValue
      count shouldBe 2

      // Cleanup
      executor.run(sqlu"DROP TABLE txn_test_success").futureValue
    }

    "return a failed Future when the action fails" in {
      val action = DBIO.failed(new RuntimeException("transaction failure"))

      val result = executor.runTransactionally(action).failed.futureValue

      result.getMessage shouldBe "transaction failure"
    }
  }
}
