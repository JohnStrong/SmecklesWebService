package org.myapps.shoppinglistservice.model;

import jakarta.persistence.*;

import static jakarta.persistence.GenerationType.IDENTITY;

@Entity
@Table(name = "shopping_list")
public class ShoppingList {

    @GeneratedValue(strategy = IDENTITY)
    @Id
    private Long id;

    public void setItem(Item item) {
        this.item = item;
    }

    @OneToOne
    @JoinColumn(name = "item_id")
    private Item item;

    public Item getItem() {
        return item;
    }

    @Column(nullable = false, unique = false)
    private Integer quantity;

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    @OneToOne(mappedBy = "shoppingList")
    private Customer customer;
}
