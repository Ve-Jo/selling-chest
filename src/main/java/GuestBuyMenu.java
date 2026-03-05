import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class GuestBuyMenu extends SellingChestMenu {

    private final SellingChest chest;
    private final SellingChestManager manager;
    private final Economy economy;

    private static final int SLOT_ITEM_INFO = 13;
    private static final int SLOT_BUY_1 = 11;
    private static final int SLOT_BUY_8 = 12;
    private static final int SLOT_BUY_32 = 14;
    private static final int SLOT_BUY_64 = 15;
    private static final int SLOT_CLOSE = 26;

    public GuestBuyMenu(JavaPlugin plugin, Player player, SellingChest chest, SellingChestManager manager, Economy economy) {
        super(plugin, player, "§6§lКупить предметы", 27);
        this.chest = chest;
        this.manager = manager;
        this.economy = economy;
    }

    @Override
    protected void initializeItems() {
        fillEmptySlots(createGuiBackground());

        Material sellingItem = chest.getSellingItem();
        int stock = chest.getStockInChest();
        double price = chest.getPricePerItem();

        if (sellingItem != null) {
            String itemName = formatItemName(sellingItem);

            List<String> lore = new ArrayList<>();
            lore.add("§7Цена: §a$" + String.format("%.2f", price) + " за шт");
            lore.add("§7В наличии: §a" + stock + " шт");
            lore.add("");
            lore.add("§7Ваш баланс: §f$" + String.format("%.2f", economy.getBalance(player)));

            setItem(SLOT_ITEM_INFO,
                createItem(sellingItem, "§a§l" + itemName, stock > 0 ? 1 : 1,
                    lore.toArray(new String[0])));
        }

        setupBuyButton(SLOT_BUY_1, 1);
        setupBuyButton(SLOT_BUY_8, 8);
        setupBuyButton(SLOT_BUY_32, 32);
        setupBuyButton(SLOT_BUY_64, 64);

        setItem(SLOT_CLOSE,
            createItem(Material.ARROW, "§e§lЗакрыть", "§7Нажмите чтобы закрыть"),
            e -> close());

        setItem(0,
            createItem(Material.BOOK, "§b§lКак покупать",
                "§7Нажмите на изумруд",
                "§7чтобы купить предметы",
                "",
                "§7Деньги спишутся с",
                "§7вашего баланса и",
                "§7отправятся владельцу"));
    }

    private void setupBuyButton(int slot, int amount) {
        double price = chest.getPricePerItem();
        double total = price * amount;
        int stock = chest.getStockInChest();
        boolean canAfford = economy.getBalance(player) >= total;
        boolean hasStock = stock >= amount;
        boolean hasSpace = getInventorySpace() >= amount;

        Material buttonMaterial;
        String buttonName;
        List<String> lore = new ArrayList<>();

        if (!hasStock) {
            buttonMaterial = Material.RED_STAINED_GLASS_PANE;
            buttonName = "§c§lКупить " + amount + " - Нет в наличии";
        } else if (!canAfford) {
            buttonMaterial = Material.RED_STAINED_GLASS_PANE;
            buttonName = "§c§lКупить " + amount + " - Недостаточно денег";
            lore.add("§7Стоимость: §c$" + String.format("%.2f", total));
            lore.add("§7Ваш баланс: §c$" + String.format("%.2f", economy.getBalance(player)));
        } else if (!hasSpace) {
            buttonMaterial = Material.RED_STAINED_GLASS_PANE;
            buttonName = "§c§lКупить " + amount + " - Нет места в инвентаре";
        } else {
            buttonMaterial = Material.EMERALD;
            buttonName = "§a§lКупить " + amount + " предметов";
            lore.add("§7Стоимость: §a$" + String.format("%.2f", total));
            lore.add("§7Нажмите для покупки!");
        }

        setItem(slot,
            createItem(buttonMaterial, buttonName, lore.toArray(new String[0])),
            e -> handleBuy(amount));
    }

    private void handleBuy(int amount) {
        Material sellingItem = chest.getSellingItem();
        if (sellingItem == null) {
            playErrorSound();
            player.sendMessage("§cЭтот сундук не настроен.");
            return;
        }

        double price = chest.getPricePerItem();
        double total = price * amount;
        int stock = chest.getStockInChest();

        if (stock < amount) {
            playErrorSound();
            player.sendMessage(formatMessage("selling-chests.messages.purchase-fail-no-stock",
                "§cЭтот сундук закончился.", sellingItem, total));
            return;
        }

        if (economy.getBalance(player) < total) {
            playErrorSound();
            player.sendMessage(formatMessage("selling-chests.messages.purchase-fail-no-money",
                "§cУ вас недостаточно средств. Цена: §f${price} §c| Ваш баланс: §f${balance}", sellingItem, total));
            return;
        }
        if (getInventorySpace() < amount) {
            playErrorSound();
            player.sendMessage(formatMessage("selling-chests.messages.purchase-fail-full-inventory",
                "§cВаш инвентарь заполнен!", sellingItem, total));
            return;
        }

        economy.withdrawPlayer(player, total);
        economy.depositPlayer(Bukkit.getOfflinePlayer(chest.getOwnerUuid()), total);

        if (!chest.removeItems(amount)) {
            economy.depositPlayer(player, total);
            economy.withdrawPlayer(Bukkit.getOfflinePlayer(chest.getOwnerUuid()), total);
            playErrorSound();
            player.sendMessage("§cОшибка транзакции! Попробуйте снова.");
            return;
        }

        ItemStack items = new ItemStack(sellingItem, amount);
        player.getInventory().addItem(items);

        playSuccessSound();
        String itemName = formatItemName(sellingItem);
        player.sendMessage(formatMessage("selling-chests.messages.purchase-success",
            "§aВы купили §f{item} §aза §f${price}§a!", sellingItem, total)
            .replace("{item}", amount + "x " + itemName));

        Player owner = Bukkit.getPlayer(chest.getOwnerUuid());
        if (owner != null && !owner.equals(player)) {
            owner.sendMessage(formatMessage("selling-chests.messages.owner-notification",
                "§e{buyer} §aкупил §f{item} §aот вашего торгового сундука за §f${price}§a!", sellingItem, total)
                .replace("{buyer}", player.getName())
                .replace("{item}", amount + "x " + itemName));
        }

        initializeItems();
    }

    private int getInventorySpace() {
        int space = 0;
        Material sellingItem = chest.getSellingItem();
        if (sellingItem == null) return 0;

        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                space += 64;
            } else if (item.getType() == sellingItem && item.getAmount() < 64) {
                space += 64 - item.getAmount();
            }
        }
        return space;
    }

    private String formatItemName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim();
    }

    private String formatMessage(String path, String fallback, Material sellingItem, double totalPrice) {
        String raw = plugin.getConfig().getString(path, fallback);
        String itemName = sellingItem != null ? formatItemName(sellingItem) : "";
        String resolved = raw
            .replace("{item}", itemName)
            .replace("${price}", String.format("%.2f", totalPrice))
            .replace("${balance}", String.format("%.2f", economy.getBalance(player)));
        return ChatColor.translateAlternateColorCodes('&', resolved);
    }
}
