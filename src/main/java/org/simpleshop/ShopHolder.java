package org.simpleshop;

import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Oznacuje otevrene GUI okno jako patrici konkretnimu shopu (cedulce).
 * Diky tomuhle pozname v InventoryClickEvent, ze hrac klika v shop menu,
 * a mame u toho hned vsechny potrebne udaje (item, cena, rezim, majitel).
 */
public class ShopHolder implements InventoryHolder {

    private final Block signBlock;
    private final ItemStack template;
    private final boolean buyFromPlayer; // true = B (majitel vykupuje), false = S (majitel prodava)
    private final double price;
    private final UUID ownerId;
    private Inventory inventory;

    public ShopHolder(Block signBlock, ItemStack template, boolean buyFromPlayer, double price, UUID ownerId) {
        this.signBlock = signBlock;
        this.template = template;
        this.buyFromPlayer = buyFromPlayer;
        this.price = price;
        this.ownerId = ownerId;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Block getSignBlock() {
        return signBlock;
    }

    public ItemStack getTemplate() {
        return template;
    }

    public boolean isBuyFromPlayer() {
        return buyFromPlayer;
    }

    public double getPrice() {
        return price;
    }

    public UUID getOwnerId() {
        return ownerId;
    }
}
