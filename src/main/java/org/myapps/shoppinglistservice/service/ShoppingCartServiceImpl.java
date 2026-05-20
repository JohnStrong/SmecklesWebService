package org.myapps.shoppinglistservice.service;

import org.myapps.shoppinglistservice.repo.ItemsRepository;
import org.myapps.shoppinglistservice.repo.ShoppingListRepository;
import org.myapps.shoppinglistservice.model.Item;
import org.myapps.shoppinglistservice.model.ShoppingList;
import org.springframework.stereotype.Service;

@Service
public class ShoppingCartServiceImpl implements ShoppingListService {

     private ShoppingListRepository shoppingListRepository;
    private ItemsRepository itemsRepository;

    public ShoppingCartServiceImpl(
        ShoppingListRepository shoppingListRepository,
        ItemsRepository itemsRepository
    ) {
        this.shoppingListRepository = shoppingListRepository;
        this.itemsRepository = itemsRepository;
    }

    @Override
    public void addItem(Long shoppingListId, String itemName) {
        ShoppingList shoppingList = shoppingListRepository.findById(shoppingListId)
            .orElseThrow(() -> new RuntimeException("Shopping list not found"));

        Item item = itemsRepository.findByName(itemName).orElseThrow(() ->
                new RuntimeException("Item not found. it must be out of stock...")
        );

        ShoppingList newEntry = new ShoppingList();
        newEntry.setItem(item);
        newEntry.setQuantity(1); // update to increase/decrease

        shoppingListRepository.save(newEntry);
    }
}
