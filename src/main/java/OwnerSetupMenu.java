import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class OwnerSetupMenu extends SellingChestMenu {

    private final SellingChest chest;
    private final SellingChestManager manager;
    private static final int SLOT_STATUS = 10;
    private static final int SLOT_ITEM = 12;
    private static final int SLOT_PRICE = 14;
    private static final int SLOT_STOCK = 16;
    private static final int SLOT_OPEN_CHEST = 18;
    private static final int SLOT_REMOVE = 22;
    private static final int SLOT_CLOSE = 26;

    public OwnerSetupMenu(JavaPlugin plugin, Player player, SellingChest chest, SellingChestManager manager) {
        super(plugin, player, "§6§lНастройка торгового сундука", 27);
        this.chest = chest;
        this.manager = manager;
    }

    @Override
    protected void initializeItems() {
        fillEmptySlots(createGuiBackground());

        updateStatusButton();
        updateItemButton();
        updatePriceButton();
        updateStockButton();

        setItem(SLOT_OPEN_CHEST,
            createItem(Material.CHEST, "§a§lОткрыть сундук",
                "§7Нажмите чтобы открыть сундук",
                "§7и добавить предметы внутрь",
                "",
                "§eЭто закроет меню",
                "§eи откроет реальный сундук"),
            this::handleOpenChest);

        setItem(SLOT_REMOVE,
            createItem(Material.BARRIER, "§c§lУдалить торговый сундук",
                "§7Нажмите чтобы удалить торговый сундук",
                "§cЭто нельзя отменить!"),
            this::handleRemove);

        setItem(SLOT_CLOSE,
            createItem(Material.ARROW, "§e§lЗакрыть меню",
                "§7Нажмите чтобы закрыть это меню"),
            e -> close());

        setItem(0,
            createItem(Material.BOOK, "§b§lИнструкция по настройке",
                "§71. §fВыберите предмет для продажи",
                "§72. §fУстановите цену за штуку",
                "§73. §fДобавьте товар, положив в сундук",
                "§74. §fВключите сундук когда будете готовы"));
    }

    private void updateStatusButton() {
        if (chest.isEnabled()) {
            setItem(SLOT_STATUS,
                createItem(Material.LIME_WOOL, "§a§lСундук включен",
                    "§7Статус: §aАктивен",
                    "§7Игроки могут покупать из этого сундука",
                    "",
                    "§eНажмите чтобы выключить"),
                this::handleToggleStatus);
        } else {
            setItem(SLOT_STATUS,
                createItem(Material.RED_WOOL, "§c§lСундук выключен",
                    "§7Статус: §cНеактивен",
                    chest.isConfigured() ? "§aГотов к включению" : "§cСначала установите предмет и цену",
                    "",
                    chest.isConfigured() ? "§eНажмите чтобы включить" : "§7Нельзя включить: не настроен"),
                e -> {
                    if (chest.isConfigured()) {
                        handleToggleStatus(e);
                    } else {
                        playErrorSound();
                        player.sendMessage("§cСначала установите предмет для продажи и цену!");
                    }
                });
        }
    }

    private void updateItemButton() {
        Material item = chest.getSellingItem();
        if (item == null) {
            setItem(SLOT_ITEM,
                createItem(Material.CHEST, "§e§lУстановить предмет",
                    "§7Текущий: §cНе установлен",
                    "",
                    "§7Нажмите с предметом в руке",
                    "§7или выберите из инвентаря"),
                this::handleSetItem);
        } else {
            String name = formatItemName(item);
            setItem(SLOT_ITEM,
                createItem(item, "§a§lТовар: §f" + name,
                    "§7Нажмите чтобы изменить предмет"),
                this::handleSetItem);
        }
    }

    private void updatePriceButton() {
        double price = chest.getPricePerItem();
        setItem(SLOT_PRICE,
            createItem(Material.GOLD_INGOT, "§e§lУстановить цену",
                "§7Текущая цена: §a$" + String.format("%.2f", price),
                "",
                "§eЛКМ: +$1",
                "§eПКМ: -$1",
                "§eШифт+ЛКМ: +$10",
                "§eШифт+ПКМ: -$10",
                "§7Или введите сумму в чат"),
            this::handleSetPrice);
    }

    private void updateStockButton() {
        int stock = chest.getStockInChest();
        Material sellingItem = chest.getSellingItem();

        List<String> lore = new ArrayList<>();
        lore.add("§7Текущий запас: §a" + stock + " шт");
        lore.add("");
        lore.add("§7Чтобы добавить товар:");
        lore.add("§f1. §7Откройте сундук физически");
        lore.add("§f2. §7Положите предметы внутрь");
        lore.add("§f3. §7Закройте и откройте меню снова");
        lore.add("");
        lore.add("§eНажмите чтобы обновить количество");

        Material displayMaterial = sellingItem != null && sellingItem != Material.AIR
            ? sellingItem
            : Material.CHEST;

        setItem(SLOT_STOCK,
            createItem(displayMaterial, "§b§lУправление запасом", stock > 0 ? Math.min(stock, 64) : 1,
                lore.toArray(new String[0])),
            e -> {
                playClickSound();
                updateStockButton();
            });
    }

    private void handleOpenChest(InventoryClickEvent e) {
        playClickSound();
        close();

        if (chest.getLocation().getBlock().getState() instanceof org.bukkit.block.Container container) {
            player.openInventory(container.getInventory());
            player.sendMessage("§aСундук открыт! Положите ваши предметы внутрь.");
        } else {
            player.sendMessage("§cНе удалось открыть сундук. Возможно, блок был удален.");
        }
    }

    private void handleToggleStatus(InventoryClickEvent e) {
        playClickSound();
        boolean newStatus = !chest.isEnabled();
        chest.setEnabled(newStatus);
        manager.saveChest(chest);

        updateStatusButton();
        player.sendMessage(newStatus ? "§aТорговый сундук включен!" : "§cТорговый сундук выключен.");
    }

    private void handleSetItem(InventoryClickEvent e) {
        playClickSound();

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.AIR) {
            chest.setSellingItem(hand.getType());
            manager.saveChest(chest);
            updateItemButton();
            player.sendMessage("§aУстановлен товар: §f" + formatItemName(hand.getType()));
        } else {
            close();
            player.sendMessage("§eДержите предмет для продажи в основной руке,");
            player.sendMessage("§eзатем снова нажмите ПКМ по сундуку!");
        }
    }

    private void handleSetPrice(InventoryClickEvent e) {
        playClickSound();

        double currentPrice = chest.getPricePerItem();
        double newPrice = currentPrice;

        if (e.isLeftClick() && e.isShiftClick()) {
            newPrice += 10;
        } else if (e.isLeftClick()) {
            newPrice += 1;
        } else if (e.isRightClick() && e.isShiftClick()) {
            newPrice = Math.max(0, currentPrice - 10);
        } else if (e.isRightClick()) {
            newPrice = Math.max(0, currentPrice - 1);
        }

        chest.setPricePerItem(newPrice);
        manager.saveChest(chest);
        updatePriceButton();

        player.sendMessage("§aЦена установлена: §f$" + String.format("%.2f", newPrice));
    }

    private void handleRemove(InventoryClickEvent e) {
        playErrorSound();
        close();
        player.sendMessage("§cЧтобы удалить сундук:");
        player.sendMessage("§7Держите палочку и §c§lШИФТ+ПКМ§7 по сундуку");
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
}
