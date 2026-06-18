import models.{Customer, ShoppingListWithItems, User}
import services.{CustomerService, CustomerServiceImpl, ShoppingListService, ShoppingListServiceImpl}
import com.google.inject.{AbstractModule, TypeLiteral}
import repositories.DataRepository
import repositories.customer.SlickCustomerRepository
import repositories.shoppinglist.SlickShoppingListRepository
import repositories.user.SlickUserRepository
import auth.{JwkProviderFactory, GoogleJwkProviderFactory}

class Module extends AbstractModule {
  override def configure(): Unit = {

    /**
     * User
     */
    bind(new TypeLiteral[DataRepository[String, User]]() {}).to(classOf[SlickUserRepository])

    /**
     * Customer
     */
    bind(new TypeLiteral[DataRepository[String, Customer]]() {}).to(classOf[SlickCustomerRepository])
    bind(classOf[CustomerService]).to(classOf[CustomerServiceImpl])

    /**
     * Shopping List
     */
    bind(new TypeLiteral[DataRepository[String, ShoppingListWithItems]]() {}).to(classOf[SlickShoppingListRepository])
    bind(classOf[ShoppingListService]).to(classOf[ShoppingListServiceImpl])

    /**
     * Auth
     */
    bind(classOf[JwkProviderFactory]).to(classOf[GoogleJwkProviderFactory])
  }
}
