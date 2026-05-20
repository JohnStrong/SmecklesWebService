package org.myapps.shoppinglistservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "customer")
public class Customer {

    @GeneratedValue
    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @OneToOne
    @JoinColumn(name = "shopping_list_id")
    private ShoppingList shoppingList;
}
