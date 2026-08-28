package org.simpleshop;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class ShopListener implements Listener {

    private static final int DISPLAY_SLOT = 13; // stredni slot v 27 slotovem GUI

    private final SimpleShop plugin;

    // KEY_ITEM obsahuje CELY serializovany ItemStack (vc. NBT - obsah shulkeru,
    // custom model data z ItemsAdderu, enchanty, jmeno...), ne jen nazev Materialu.
    private final NamespacedKey KEY_ITEM;
    private final NamespacedKey KEY_OWNER;
    private final NamespacedKey KEY_MODE;
    private final NamespacedKey KEY_PRICE;

    public ShopListener(SimpleShop plugin) {
        this.plugin = plugin;
        this.KEY_ITEM = new NamespacedKey(plugin, "item");
        this.KEY_OWNER = new NamespacedKey(plugin, "owner");
        this.KEY_MODE = new NamespacedKey(plugin, "mode");
        this.KEY_PRICE = new NamespacedKey(plugin, "price");
    }

    // ---------------------------------------------------------------
    // Vytvoreni cedule
    // ---------------------------------------------------------------
    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line0 = event.getLine(0) == null ? "" : ChatColor.stripColor(event.getLine(0)).trim();
        if (!line0.equalsIgnoreCase("[shop]")) {
            return; // neni to nase cedule
        }

        Player player = event.getPlayer();

        // radek 2 - cena
        double price;
        try {
            String raw = event.getLine(1) == null ? "" : event.getLine(1).replace(",", ".").trim();
            price = Double.parseDouble(raw);
            if (price <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Druhy radek musi byt kladne cislo (cena), napr. 100");
            cancelSign(event);
            return;
        }

        // radek 3 - B nebo S
        String modeRaw = event.getLine(2) == null ? "" : event.getLine(2).trim();
        if (!modeRaw.equalsIgnoreCase("B") && !modeRaw.equalsIgnoreCase("S")) {
            player.sendMessage(ChatColor.RED + "Treti radek musi byt 'B' (vykup od hracu) nebo 'S' (prodej hracum)");
            cancelSign(event);
            return;
        }
        boolean buyFromPlayer = modeRaw.equalsIgnoreCase("B");

        // najit truhlu za/pod cedulí
        Block chestBlock = getAttachedContainer(event.getBlock());
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            player.sendMessage(ChatColor.RED + "Cedule musi byt postavena na truhle nebo pripevnena na jeji predni stranu.");
            cancelSign(event);
            return;
        }

        Chest chest = (Chest) chestBlock.getState();
        ItemStack found = null;
        for (ItemStack item : chest.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                found = item;
                break;
            }
        }

        if (found == null) {
            player.sendMessage(ChatColor.RED + "Nejdriv vloz do truhly alespon 1 kus itemu (klidne i shulker s vecma nebo custom item), "
                    + "ktery chces " + (buyFromPlayer ? "vykupovat." : "prodavat.") + " Pak postav ceduli znovu.");
            cancelSign(event);
            return;
        }

        // ulozime si PRESNOU kopii itemu (1 ks) - vc. veskereho NBT (obsah shulkeru,
        // custom model data, jmeno, enchanty...), aby plugin pri obchodu vracel/bral
        // presne tenhle item a ne "cistou" vanilla verzi podle typu.
        final ItemStack template = found.clone();
        template.setAmount(1);
        final String serialized = serializeItem(template);

        // vzhled cedule
        event.setLine(0, ChatColor.GREEN + "[Shop]");
        event.setLine(1, formatPrice(price));
        event.setLine(2, buyFromPlayer ? ChatColor.GOLD + "VYKUP (B)" : ChatColor.AQUA + "PRODEJ (S)");
        event.setLine(3, formatItemName(template));

        final double finalPrice = price;
        final boolean finalBuyFromPlayer = buyFromPlayer;
        final Block signBlock = event.getBlock();
        final UUID ownerId = player.getUniqueId();

        // ulozeni dat do cedule - az po zapsani bloku, proto o tick pozdeji
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (signBlock.getState() instanceof Sign) {
                Sign sign = (Sign) signBlock.getState();
                PersistentDataContainer pdc = sign.getPersistentDataContainer();
                pdc.set(KEY_ITEM, PersistentDataType.STRING, serialized);
                pdc.set(KEY_OWNER, PersistentDataType.STRING, ownerId.toString());
                pdc.set(KEY_MODE, PersistentDataType.STRING, finalBuyFromPlayer ? "B" : "S");
                pdc.set(KEY_PRICE, PersistentDataType.DOUBLE, finalPrice);
                sign.update(true, false);
            }
        });

        player.sendMessage(ChatColor.GREEN + "Shop vytvoren: " + (buyFromPlayer ? "vykupujes " : "prodavas ")
                + formatItemName(template) + " za " + formatPrice(price) + "/ks");
    }

    private void cancelSign(SignChangeEvent event) {
        event.setLine(0, ChatColor.RED + "[Chyba]");
        event.setLine(1, "");
        event.setLine(2, "");
        event.setLine(3, "");
    }

    // ---------------------------------------------------------------
    // Klik na cedulku -> OTEVRE GUI (nakup/prodej se resi az kliknutim v GUI)
    // ---------------------------------------------------------------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Sign)) return;

        Sign sign = (Sign) block.getState();
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        if (!pdc.has(KEY_ITEM, PersistentDataType.STRING)) return; // neni to shop cedule

        event.setCancelled(true);
        Player player = event.getPlayer();

        String itemData = pdc.get(KEY_ITEM, PersistentDataType.STRING);
        String ownerUuidStr = pdc.get(KEY_OWNER, PersistentDataType.STRING);
        String mode = pdc.get(KEY_MODE, PersistentDataType.STRING);
        Double price = pdc.get(KEY_PRICE, PersistentDataType.DOUBLE);

        if (itemData == null || ownerUuidStr == null || mode == null || price == null) {
            player.sendMessage(ChatColor.RED + "Tato cedule je poskozena.");
            return;
        }

        ItemStack template = deserializeItem(itemData);
        UUID ownerId = UUID.fromString(ownerUuidStr);
        boolean buyFromPlayer = "B".equals(mode);

        ShopHolder holder = new ShopHolder(block, template, buyFromPlayer, price, ownerId);
        String title = (buyFromPlayer ? ChatColor.GOLD + "Vykup: " : ChatColor.AQUA + "Prodej: ") + formatItemName(template);
        Inventory inv = Bukkit.createInventory(holder, 27, title);
        holder.setInventory(inv);

        fillBorders(inv);
        refreshDisplay(inv, holder);

        player.openInventory(inv);
    }

    // vyplni okraje GUI sedym sklem, aby slo kliknout jen na prostredni slot s itemem
    private void fillBorders(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (i != DISPLAY_SLOT) {
                inv.setItem(i, filler);
            }
        }
    }

    // aktualizuje polozku v GUI (item + popisek s cenou, skladem a instrukci)
    private void refreshDisplay(Inventory inv, ShopHolder holder) {
        ItemStack display = holder.getTemplate().clone();
        ItemMeta meta = display.getItemMeta();
        List<String> lore = new ArrayList<>();
        if (meta != null && meta.hasLore() && meta.getLore() != null) {
            lore.addAll(meta.getLore());
            lore.add("");
        }

        Block chestBlock = getAttachedContainer(holder.getSignBlock());
        int stock = -1;
        if (chestBlock != null && chestBlock.getState() instanceof Chest) {
            Inventory chestInv = ((Chest) chestBlock.getState()).getInventory();
            stock = countMatching(chestInv, holder.getTemplate());
        }

        lore.add(ChatColor.GRAY + "Cena: " + ChatColor.WHITE + formatPrice(holder.getPrice()) + " / ks");

        if (holder.isBuyFromPlayer()) {
            lore.add(ChatColor.YELLOW + "Klikni pro prodej 1 ks");
            lore.add(ChatColor.YELLOW + "Shift+klik pro prodej cele stacky");
        } else {
            if (stock >= 0) {
                lore.add(ChatColor.GRAY + "Sklad: " + ChatColor.WHITE + stock + " ks");
            }
            lore.add(ChatColor.YELLOW + "Klikni pro koupi 1 ks");
            lore.add(ChatColor.YELLOW + "Shift+klik pro koupi cele stacky");
        }

        if (meta != null) {
            meta.setLore(lore);
            display.setItemMeta(meta);
        }
        inv.setItem(DISPLAY_SLOT, display);
    }

    // ---------------------------------------------------------------
    // Klik uvnitr GUI - tady se skutecne provede nakup/prodej
    // ---------------------------------------------------------------
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof ShopHolder)) return;

        event.setCancelled(true); // v tomhle GUI se nesmi nic presouvat/brat

        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInventory)) return;
        if (event.getSlot() != DISPLAY_SLOT) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        ShopHolder holder = (ShopHolder) topInventory.getHolder();
        Player player = (Player) event.getWhoClicked();

        Block chestBlock = getAttachedContainer(holder.getSignBlock());
        if (chestBlock == null || !(chestBlock.getState() instanceof Chest)) {
            player.sendMessage(ChatColor.RED + "Truhla tohoto shopu chybi nebo byla znicena.");
            player.closeInventory();
            return;
        }
        Chest chest = (Chest) chestBlock.getState();
        Inventory chestInv = chest.getInventory();

        OfflinePlayer owner = Bukkit.getOfflinePlayer(holder.getOwnerId());
        Economy econ = SimpleShop.getEconomy();
        ItemStack template = holder.getTemplate();

        int amount = event.getClick().isShiftClick() ? template.getMaxStackSize() : 1;

        if (holder.isBuyFromPlayer()) {
            handlePlayerSells(player, owner, econ, chestInv, template, holder.getPrice(), amount);
        } else {
            handlePlayerBuys(player, owner, econ, chestInv, template, holder.getPrice(), amount);
        }

        // po transakci aktualizujeme popisek (napr. novy stav skladu), GUI zustava otevrene
        refreshDisplay(topInventory, holder);
    }

    // zablokuje tazeni/drag itemu v shop GUI
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    // Majitel PRODAVA (S) -> hrac KUPUJE z truhly
    private void handlePlayerBuys(Player player, OfflinePlayer owner, Economy econ, Inventory chestInv,
                                   ItemStack template, double price, int amount) {
        int available = countMatching(chestInv, template);
        if (available <= 0) {
            player.sendMessage(ChatColor.RED + "Shop je vyprodany.");
            return;
        }
        amount = Math.min(amount, available);

        double total = price * amount;
        if (econ.getBalance(player) < total) {
            player.sendMessage(ChatColor.RED + "Nemas dostatek penez. Potrebujes " + formatPrice(total));
            return;
        }

        econ.withdrawPlayer(player, total);
        econ.depositPlayer(owner, total);
        removeMatching(chestInv, template, amount);

        ItemStack giveStack = template.clone();
        giveStack.setAmount(amount);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(giveStack);
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }

        player.sendMessage(ChatColor.GREEN + "Koupil jsi " + amount + "x " + formatItemName(template)
                + " za " + formatPrice(total));
    }

    // Majitel VYKUPUJE (B) -> hrac PRODAVA do truhly
    private void handlePlayerSells(Player player, OfflinePlayer owner, Economy econ, Inventory chestInv,
                                    ItemStack template, double price, int amount) {
        int playerHas = countMatching(player.getInventory(), template);
        if (playerHas <= 0) {
            player.sendMessage(ChatColor.RED + "Nemas co prodat - potrebujes " + formatItemName(template));
            return;
        }
        amount = Math.min(amount, playerHas);

        double total = price * amount;
        if (econ.getBalance(owner) < total) {
            player.sendMessage(ChatColor.RED + "Majitel shopu nema dostatek penez na vykup.");
            return;
        }

        int space = freeSpaceForTemplate(chestInv, template);
        if (space <= 0) {
            player.sendMessage(ChatColor.RED + "Truhla shopu je plna, nelze prodat.");
            return;
        }
        amount = Math.min(amount, space);
        total = price * amount;

        removeMatching(player.getInventory(), template, amount);
        ItemStack addStack = template.clone();
        addStack.setAmount(amount);
        chestInv.addItem(addStack);
        econ.withdrawPlayer(owner, total);
        econ.depositPlayer(player, total);

        player.sendMessage(ChatColor.GREEN + "Prodal jsi " + amount + "x " + formatItemName(template)
                + " za " + formatPrice(total));
    }

    // ---------------------------------------------------------------
    // Ochrana - nikdo krome majitele/opa nemuze rozbit cedulku shopu
    // ---------------------------------------------------------------
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Sign)) return;

        Sign sign = (Sign) block.getState();
        PersistentDataContainer pdc = sign.getPersistentDataContainer();
        if (!pdc.has(KEY_ITEM, PersistentDataType.STRING)) return;

        Player player = event.getPlayer();
        if (player.isOp() || player.hasPermission("simpleshop.admin")) return;

        String ownerUuidStr = pdc.get(KEY_OWNER, PersistentDataType.STRING);
        if (ownerUuidStr != null && ownerUuidStr.equals(player.getUniqueId().toString())) return;

        event.setCancelled(true);
        player.sendMessage(ChatColor.RED + "Tuto cedulku shopu muze zbourat jen jeji majitel.");
    }

    // ---------------------------------------------------------------
    // Pomocne metody - praci s inventarem delame porovnanim CELYCH
    // itemu (isSimilar), ne jen Material -> funguje pro shulkery s obsahem,
    // ItemsAdder itemy, enchantnute a pojmenovane itemy atd.
    // ---------------------------------------------------------------
    private Block getAttachedContainer(Block signBlock) {
        BlockData data = signBlock.getBlockData();
        if (data instanceof WallSign) {
            WallSign wallSign = (WallSign) data;
            return signBlock.getRelative(wallSign.getFacing().getOppositeFace());
        }
        return signBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
    }

    private int countMatching(Inventory inv, ItemStack template) {
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.isSimilar(template)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeMatching(Inventory inv, ItemStack template, int amount) {
        int remaining = amount;
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(template)) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                if (item.getAmount() <= 0) {
                    inv.setItem(i, null);
                } else {
                    inv.setItem(i, item);
                }
                remaining -= take;
            }
        }
    }

    private int freeSpaceForTemplate(Inventory inv, ItemStack template) {
        int space = 0;
        int maxStack = template.getMaxStackSize();
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                space += maxStack;
            } else if (item.isSimilar(template)) {
                space += (maxStack - item.getAmount());
            }
        }
        return space;
    }

    private String formatPrice(double price) {
        if (price == Math.floor(price)) {
            return String.valueOf((long) price);
        }
        return String.format("%.2f", price);
    }

    private String formatItemName(ItemStack item) {
        String name;
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        } else {
            name = item.getType().name().replace("_", " ").toLowerCase();
        }
        if (name.length() > 14) {
            name = name.substring(0, 14);
        }
        return name;
    }

    // Serializace celeho ItemStacku (vc. NBT) do textu, aby se dal ulozit
    // do PersistentDataContainer cedule (ktery umi jen zakladni typy jako String).
    private String serializeItem(ItemStack item) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Nepodarilo se ulozit item do cedule", e);
        }
    }

    private ItemStack deserializeItem(String data) {
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("Nepodarilo se nacist item z cedule", e);
        }
    }
}
