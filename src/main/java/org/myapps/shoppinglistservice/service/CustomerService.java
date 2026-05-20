package org.myapps.shoppinglistservice.service;

import org.myapps.shoppinglistservice.model.Customer;

import java.util.Optional;

public interface CustomerService {
    void createCustomer(String email);
    Optional<Customer> getCustomer(Long id);
}
