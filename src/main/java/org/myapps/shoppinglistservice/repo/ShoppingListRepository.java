package org.myapps.shoppinglistservice.repo;

import org.myapps.shoppinglistservice.model.ShoppingList;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShoppingListRepository extends JpaRepository<ShoppingList, Long> {

}
