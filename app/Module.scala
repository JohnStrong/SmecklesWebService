import services.{CustomerService, CustomerServiceImpl}

import com.google.inject.AbstractModule

class Module extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CustomerService]).to(classOf[CustomerServiceImpl])
  }
}
