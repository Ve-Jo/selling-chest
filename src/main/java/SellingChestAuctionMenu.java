import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SellingChestAuctionMenu extends SellingChestMenu {

    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final SellingChestManager manager;
    private final Economy economy;
    private final List<SellingChest> chests;
    private final int page;
    private final int itemsPerPage;

    public SellingChestAuctionMenu(JavaPlugin plugin, Player player, SellingChestManager manager, Economy economy, int page) {
        super(plugin, player, resolveTitle(plugin), resolveSize(plugin));
        this.manager = manager;
        this.economy = economy;
        this.page = Math.max(0, page);
        this.itemsPerPage = Math.max(1, size - 9);
        this.chests = manager.getAllChests().stream()
            .filter(chest -> chest.isEnabled() && chest.isConfigured())
            .sorted(Comparator
                .comparing((SellingChest chest) -> formatMaterialName(chest.getSellingItem()))
                .thenComparing(chest -> chest.getOwnerUuid().toString()))
            .toList();
    }

    @Override
    protected void initializeItems() {
        inventory.clear();
        clickHandlers.clear();

        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(chests.size(), startIndex + itemsPerPage);
        int slot = 0;

        for (int i = startIndex; i < endIndex; i++) {
            SellingChest chest = chests.get(i);
            setItem(slot, createChestItem(chest), event -> handleChestClick(event, chest));
            slot++;
        }

        fillEmptySlots(createGuiBackground());
        setupControls();
    }

    private void handleChestClick(InventoryClickEvent event, SellingChest chest) {
        if (economy == null) {
            playErrorSound();
            player.sendMessage("§cЭкономика недоступна. Обратитесь к администратору.");
            return;
        }
        if (!chest.isEnabled() || !chest.isConfigured()) {
            playErrorSound();
            player.sendMessage("§cЭтот торговый сундук сейчас недоступен.");
            return;
        }
        if (chest.getStockInChest() <= 0) {
            playErrorSound();
            player.sendMessage("§cЭтот торговый сундук пуст.");
            return;
        }
        playClickSound();
        new GuestBuyMenu(plugin, player, chest, manager, economy).open();
    }

    private void setupControls() {
        int maxPage = chests.isEmpty() ? 0 : (chests.size() - 1) / itemsPerPage;

        if (page > 0) {
            setItem(SLOT_PREV,
                createItem(Material.ARROW, "§e§lНазад", "§7Страница " + page + " из " + (maxPage + 1)),
                event -> {
                    playClickSound();
                    new SellingChestAuctionMenu(plugin, player, manager, economy, page - 1).open();
                });
        } else {
            setItem(SLOT_PREV, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        setItem(SLOT_INFO,
            createItem(Material.BOOK, "§b§lТорговые сундуки",
                "§7Страница: §f" + (page + 1) + "§7/§f" + (maxPage + 1),
                "§7Доступно: §f" + chests.size()));

        if (page < maxPage) {
            setItem(SLOT_NEXT,
                createItem(Material.ARROW, "§e§lВперед", "§7Страница " + (page + 2) + " из " + (maxPage + 1)),
                event -> {
                    playClickSound();
                    new SellingChestAuctionMenu(plugin, player, manager, economy, page + 1).open();
                });
        } else {
            setItem(SLOT_NEXT, createItem(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
    }

    private ItemStack createChestItem(SellingChest chest) {
        Material displayMaterial = chest.getSellingItem() != null ? chest.getSellingItem() : Material.CHEST;
        int stock = chest.getStockInChest();
        double price = chest.getPricePerItem();
        OfflinePlayer owner = Bukkit.getOfflinePlayer(chest.getOwnerUuid());

        List<String> lore = new ArrayList<>();
        if (chest.getDisplayName() != null && !chest.getDisplayName().isBlank()) {
            lore.add("§f" + chest.getDisplayName());
            lore.add(" ");
        }
        lore.add("§7Товар: §f" + formatMaterialName(chest.getSellingItem()));
        lore.add("§7Цена: §a$" + String.format(Locale.US, "%.2f", price) + " §7за шт");
        lore.add("§7В наличии: §a" + stock + " шт");
        lore.add("§7Владелец: §f" + (owner.getName() != null ? owner.getName() : owner.getUniqueId()));
        lore.add(" ");
        lore.add(stock > 0 ? "§eНажмите чтобы открыть" : "§cНет товара");

        String itemName = "§6§lТорговый сундук";
        if (chest.getSellingItem() != null) {
            itemName = "§a§l" + formatMaterialName(chest.getSellingItem());
        }

        return createItem(displayMaterial, itemName, Math.max(1, Math.min(stock, 64)), lore.toArray(new String[0]));
    }

    private static String formatMaterialName(Material material) {
        if (material == null) {
            return "Не задан";
        }
        String name = material.name().replace("_", " ").toLowerCase(Locale.ENGLISH);
        String[] words = name.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(word.substring(0, 1).toUpperCase()).append(word.substring(1)).append(" ");
            }
        }
        return result.toString().trim();
    }

    private static String resolveTitle(JavaPlugin plugin) {
        return plugin.getConfig().getString("selling-chests.auction-menu.title", "§6§lАукцион торговых сундуков");
    }

    private static int resolveSize(JavaPlugin plugin) {
        int configured = plugin.getConfig().getInt("selling-chests.auction-menu.size", 54);
        int clamped = Math.max(9, Math.min(54, configured));
        if (clamped % 9 != 0) {
            return 54;
        }
        return clamped;
    }
}
