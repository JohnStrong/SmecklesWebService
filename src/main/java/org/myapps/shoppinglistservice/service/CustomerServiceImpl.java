package org.myapps.shoppinglistservice.service;

import org.myapps.shoppinglistservice.model.Customer;
import org.myapps.shoppinglistservice.repo.CustomerRepository;
import org.myapps.shoppinglistservice.repo.ShoppingListRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerServiceImpl implements CustomerService{

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(
        CustomerRepository customerRepository,
        ShoppingListRepository shoppingListRepository
    ) {
       this.customerRepository = customerRepository;
    }

    @Override
    public void createCustomer(String email) {
        if (customerRepository.findByEmail(email).isPresent()) {
            return; // nothing to do, fix to return already exist exception
        }

        Customer newCustomer = new Customer();
        newCustomer.setEmail(email);
        customerRepository.save(newCustomer);
    }

    @Override
    public Optional<Customer> getCustomer(Long id) {
        return customerRepository.findById(id);
    }
}
