package repositories

import models.Customer
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.db.slick.DatabaseConfigProvider
import repositories.customer.SlickCustomerRepository
import slick.jdbc.JdbcProfile

/**
 * Provides a `withCustomer` helper for repository integration tests.
 * Creates a user and customer in the DB before running the test.
 */
trait RepositoryTestFixture extends ScalaFutures { self: GuiceOneAppPerTest =>

  protected def customerRepository: SlickCustomerRepository

  protected def withCustomer(email: String)(test: Long => Any): Unit = {
    val dbConfigProvider = app.injector.instanceOf[DatabaseConfigProvider]
    val dbConfig = dbConfigProvider.get[JdbcProfile]
    import dbConfig.profile.api.*
    val userId = dbConfig.db.run(
      sqlu"INSERT INTO users (email) VALUES ('test@user.com')"
        .andThen(sql"SELECT id FROM users WHERE email = 'test@user.com'".as[Long].head)
    ).futureValue
    customerRepository.create(Customer(email = email, userId = userId)).futureValue
    test(userId)
  }

  protected def withCustomer(email: String)(test: => Any): Unit =
    withCustomer(email)((_: Long) => test)
}
