package org.myapps.shoppinglistservice.service

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.*
import org.myapps.shoppinglistservice.model.Customer
import org.myapps.shoppinglistservice.repo.{CustomerRepository, ShoppingListRepository}
import java.util.Optional

class CustomerServiceImplSpec extends AnyWordSpec with Matchers {

  private def createFixture() = {
    val mockCustomerRepo = mock(classOf[CustomerRepository])
    val mockShoppingListRepo = mock(classOf[ShoppingListRepository])
    val service = new CustomerServiceImpl(mockCustomerRepo, mockShoppingListRepo)
    (service, mockCustomerRepo)
  }

  "CustomerServiceImpl" should {

    "create a new customer when email does not exist" in {
      val (service, mockRepo) = createFixture()
      when(mockRepo.findByEmail("new@example.com")).thenReturn(Optional.empty())

      service.createCustomer("new@example.com")

      verify(mockRepo).save(argThat[Customer](c => c.getEmail == "new@example.com"))
    }

    "not save when email already exists" in {
      val (service, mockRepo) = createFixture()
      val existing = new Customer()
      existing.setEmail("exists@example.com")
      when(mockRepo.findByEmail("exists@example.com")).thenReturn(Optional.of(existing))

      service.createCustomer("exists@example.com")

      verify(mockRepo, never()).save(any(classOf[Customer]))
    }

    "return a customer by id when found" in {
      val (service, mockRepo) = createFixture()
      val customer = new Customer()
      customer.setEmail("found@example.com")
      when(mockRepo.findById(1L)).thenReturn(Optional.of(customer))

      val result = service.getCustomer(1L)

      result.isPresent shouldBe true
      result.get().getEmail shouldBe "found@example.com"
    }

    "return empty when customer not found by id" in {
      val (service, mockRepo) = createFixture()
      when(mockRepo.findById(99L)).thenReturn(Optional.empty())

      val result = service.getCustomer(99L)

      result.isPresent shouldBe false
    }
  }
}
