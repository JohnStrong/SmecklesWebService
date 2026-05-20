package org.myapps.shoppinglistservice.repo;

import org.myapps.shoppinglistservice.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemsRepository extends JpaRepository<Item, Long> {
    Optional<Item> findByName(String name);
}
