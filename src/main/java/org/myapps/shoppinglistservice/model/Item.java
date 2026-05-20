package org.myapps.shoppinglistservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "items")
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = false)
    private String name;

    public String getName () {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
