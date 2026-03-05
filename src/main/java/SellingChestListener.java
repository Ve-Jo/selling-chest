import de.oliver.fancyholograms.api.FancyHologramsPlugin;
import de.oliver.fancyholograms.api.HologramManager;
import de.oliver.fancyholograms.api.data.HologramData;
import de.oliver.fancyholograms.api.data.ItemHologramData;
import de.oliver.fancyholograms.api.data.TextHologramData;
import de.oliver.fancyholograms.api.hologram.Hologram;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class SellingChestListener implements Listener {

    private static final String DEFAULT_ISLAND_KEY = "global";

    private final JavaPlugin plugin;
    private final SellingChestManager chestManager;
    private final NamespacedKey wandKey;
    private Economy economy;
    private BukkitTask hologramUpdateTask;

    public SellingChestListener(JavaPlugin plugin, SellingChestManager chestManager, NamespacedKey wandKey) {
        this.plugin = plugin;
        this.chestManager = chestManager;
        this.wandKey = wandKey;
        setupEconomy();
        startHologramUpdateTask();
    }

    private void setupEconomy() {
        try {
            var rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
                plugin.getLogger().info("Connected to economy: " + economy.getName());
            } else {
                plugin.getLogger().warning("No economy provider found! Economy features will be disabled.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load economy: " + e.getMessage());
        }
    }

    private void startHologramUpdateTask() {
        long interval = Math.max(1L, plugin.getConfig().getLong("selling-chests.update-interval-seconds", 5));
        long intervalTicks = 20L * interval;
        hologramUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAllHolograms();
            }
        }.runTaskTimer(plugin, 20L, intervalTicks);
    }

    public void stop() {
        if (hologramUpdateTask != null) {
            hologramUpdateTask.cancel();
        }
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        for (SellingChest chest : chestManager.getAllChests()) {
            manager.getHologram(chest.getHologramName()).ifPresent(manager::removeHologram);
            manager.getHologram(chest.getHologramName() + "_item").ifPresent(manager::removeHologram);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null || !isValidChestType(block.getType())) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        if (hand.hasItemMeta()) {
            var pdc = hand.getItemMeta().getPersistentDataContainer();
            if (pdc.has(wandKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                if (player.isSneaking()) {
                    SellingChest chest = chestManager.getChestAt(block.getLocation());
                    if (chest != null) {
                        if (chest.getOwnerUuid().equals(player.getUniqueId()) || player.hasPermission("sellingchest.admin")) {
                            removeHologram(chest);
                            chestManager.removeChest(chest.getId());
                            player.sendMessage("§aТорговый сундук удален!");
                        } else {
                            player.sendMessage("§cЭто не ваш сундук.");
                        }
                    } else {
                        player.sendMessage("§cЭто не торговый сундук.");
                    }
                } else {
                    if (!isIslandMember(player, block.getLocation())) {
                        player.sendMessage("§cВы должны быть членом острова, чтобы создать здесь торговый сундук.");
                        return;
                    }

                    String islandKey = getIslandKey(player, block.getLocation());
                    if (islandKey == null) {
                        player.sendMessage("§cВы должны быть на острове, чтобы создать торговый сундук.");
                        return;
                    }
                    SellingChest existing = chestManager.getChestAt(block.getLocation());
                    if (existing != null) {
                        player.sendMessage("§cЭтот сундук уже является торговым.");
                        return;
                    }
                    if (!isChestEmpty(block)) {
                        player.sendMessage("§cСундук должен быть пустым для преобразования. Удалите все предметы.");
                        return;
                    }

                    SellingChest chest = chestManager.createChest(block.getLocation(), islandKey, player.getUniqueId());
                    if (chest != null) {
                        updateHologram(chest);
                        player.sendMessage("§aТорговый сундук создан!");
                        player.sendMessage("§7ПКМ для настройки.");
                    }
                }
                return;
            }
        }

        if (player.isSneaking()) {
            return;
        }

        SellingChest chest = chestManager.getChestAt(block.getLocation());
        if (chest == null) return;

        if (chest.getOwnerUuid().equals(player.getUniqueId()) || player.hasPermission("sellingchest.admin")) {
            event.setCancelled(true);
            new OwnerSetupMenu(plugin, player, chest, chestManager).open();
            return;
        }

        if (chest.isEnabled() && chest.isConfigured()) {
            event.setCancelled(true);
            if (economy != null) {
                new GuestBuyMenu(plugin, player, chest, chestManager, economy).open();
            } else {
                player.sendMessage("§cЭкономика недоступна. Обратитесь к администратору.");
            }
        } else if (!chest.isEnabled()) {
            event.setCancelled(true);
            player.sendMessage("§cЭтот торговый сундук отключен.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Container container)) return;

        SellingChest chest = chestManager.getChestAt(container.getLocation());
        if (chest == null) return;

        Material sellingItem = chest.getSellingItem();
        if (sellingItem == null || sellingItem == Material.AIR) return;

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getInventory())) {
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                return;
            }
        }

        ItemStack cursor = event.getCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            if (cursor.getType() != sellingItem) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage("§cThis chest only accepts §e" + formatItemName(sellingItem) + "§c!");
                }
            }
        }

        if (event.isShiftClick() && event.getCurrentItem() != null) {
            if (event.getCurrentItem().getType() != sellingItem) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    player.sendMessage("§cThis chest only accepts §e" + formatItemName(sellingItem) + "§c!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof Container container)) return;

        SellingChest chest = chestManager.getChestAt(container.getLocation());
        if (chest == null) return;

        Material sellingItem = chest.getSellingItem();
        if (sellingItem == null || sellingItem == Material.AIR) return;

        ItemStack item = event.getOldCursor();
        if (item.getType() != sellingItem) {
            for (int slot : event.getRawSlots()) {
                if (slot < event.getInventory().getSize()) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player player) {
                        player.sendMessage("§cЭтот сундук принимает только §e" + formatItemName(sellingItem) + "§c!");
                    }
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        Inventory dest = event.getDestination();
        InventoryHolder holder = dest.getHolder();
        if (!(holder instanceof Container container)) return;

        SellingChest chest = chestManager.getChestAt(container.getLocation());
        if (chest == null) return;

        Material sellingItem = chest.getSellingItem();
        if (sellingItem == null || sellingItem == Material.AIR) return;

        if (event.getItem().getType() != sellingItem) {
            event.setCancelled(true);
        }
    }

    private boolean isChestEmpty(Block block) {
        if (!(block.getState() instanceof Container container)) return false;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR) {
                return false;
            }
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!isValidChestType(block.getType())) return;

        SellingChest chest = chestManager.getChestAt(block.getLocation());
        if (chest == null) return;

        Player player = event.getPlayer();
        if (!chest.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("sellingchest.admin")) {
            event.setCancelled(true);
            player.sendMessage("§cВы не можете сломать этот торговый сундук. Используйте §f/sellchest remove §cили палочку (шифт+ПКМ) для удаления.");
            return;
        }

        removeHologram(chest);
        chestManager.removeChest(chest.getId());
        player.sendMessage("§aТорговый сундук удален!");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (!isValidChestType(block.getType())) return false;
            SellingChest chest = chestManager.getChestAt(block.getLocation());
            if (chest != null) {
                removeHologram(chest);
                chestManager.removeChest(chest.getId());
                return true;
            }
            return false;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            if (!isValidChestType(block.getType())) return false;
            SellingChest chest = chestManager.getChestAt(block.getLocation());
            if (chest != null) {
                removeHologram(chest);
                chestManager.removeChest(chest.getId());
                return true;
            }
            return false;
        });
    }

    private void updateHologram(SellingChest chest) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();

        String hologramName = chest.getHologramName();
        double hologramYOffset = plugin.getConfig().getDouble("selling-chests.hologram-y-offset", 1.5);
        Location hologramLoc = chest.getLocation().clone().add(0.5, hologramYOffset, 0.5);

        Hologram existing = manager.getHologram(hologramName).orElse(null);

        List<String> lines = new ArrayList<>();

        if (chest.isEnabled() && chest.isConfigured()) {
            String itemName = chest.getDisplayName() != null ? chest.getDisplayName() : formatItemName(chest.getSellingItem());
            int stock = chest.getStockInChest();
            double price = chest.getPricePerItem();

            lines.add("§6§l⬇ ТОРГОВЫЙ СУНДУК ⬇");
            lines.add("§e" + itemName);
            lines.add(String.format("§7Цена: §a$%.2f §7за шт", price));
            lines.add("§7В наличии: §f" + stock + " шт");
            lines.add(stock > 0 ? "§a§lНАЖМИТЕ ДЛЯ ПОКУПКИ" : "§c§lНЕТ В НАЛИЧИИ");
        } else {
            lines.add("§6§lТОРГОВЫЙ СУНДУК");
            lines.add("§cНе настроен");
            lines.add("§7Попросите владельца настроить!");
        }

        if (existing != null) {
            HologramData data = existing.getData();
            if (data instanceof TextHologramData textData) {
                trySetHologramLocation(textData, hologramLoc);
                textData.setText(lines);
                existing.queueUpdate();
                updateItemDisplayHologram(chest, manager);
                return;
            }
            manager.removeHologram(existing);
        }

        TextHologramData data = new TextHologramData(hologramName, hologramLoc);
        data.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
        data.setTextAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
        data.setScale(new org.joml.Vector3f(1.0f, 1.0f, 1.0f));
        data.setText(lines);
        data.setSeeThrough(false);
        data.setPersistent(false);

        Hologram hologram = manager.create(data);
        manager.addHologram(hologram);

        updateItemDisplayHologram(chest, manager);
    }

    private void updateItemDisplayHologram(SellingChest chest, HologramManager manager) {
        String itemHologramName = chest.getHologramName() + "_item";
        double hologramYOffset = plugin.getConfig().getDouble("selling-chests.hologram-y-offset", 1.5);
        Location itemLoc = chest.getLocation().clone().add(0.5, hologramYOffset - 0.5, 0.5);

        Material sellingItem = chest.getSellingItem();
        Hologram existingItem = manager.getHologram(itemHologramName).orElse(null);

        if (sellingItem == null || sellingItem == Material.AIR) {
            if (existingItem != null) {
                manager.removeHologram(existingItem);
            }
            return;
        }

        if (existingItem != null) {
            manager.removeHologram(existingItem);
        }

        try {
            ItemStack itemStack = new ItemStack(sellingItem);
            ItemHologramData itemData = new ItemHologramData(itemHologramName, itemLoc);
            itemData.setItemStack(itemStack);
            itemData.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            itemData.setScale(new org.joml.Vector3f(0.6f, 0.6f, 0.6f));
            itemData.setPersistent(false);

            Hologram itemHologram = manager.create(itemData);
            manager.addHologram(itemHologram);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create item hologram: " + e.getMessage());
        }
    }

    private void removeHologram(SellingChest chest) {
        HologramManager manager = FancyHologramsPlugin.get().getHologramManager();
        manager.getHologram(chest.getHologramName()).ifPresent(manager::removeHologram);
        manager.getHologram(chest.getHologramName() + "_item").ifPresent(manager::removeHologram);
    }

    private void updateAllHolograms() {
        for (SellingChest chest : chestManager.getAllChests()) {
            updateHologram(chest);
        }
    }

    private void trySetHologramLocation(Object hologramData, Location location) {
        if (hologramData == null || location == null) return;
        for (String methodName : List.of("setLocation", "setPosition")) {
            try {
                var method = hologramData.getClass().getMethod(methodName, Location.class);
                method.setAccessible(true);
                method.invoke(hologramData, location);
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Exception ignored) {
                return;
            }
        }
    }

    private String getIslandKey(Player player, Location location) {
        Plugin iridium = Bukkit.getPluginManager().getPlugin("IridiumSkyblock");
        if (iridium != null && iridium.isEnabled()) {
            try {
                Class<?> apiClass = Class.forName("com.iridium.iridiumskyblock.api.IridiumSkyblockAPI");
                var getInstance = apiClass.getMethod("getInstance");
                var api = getInstance.invoke(null);
                var getIslandViaLocation = apiClass.getMethod("getIslandViaLocation", Location.class);
                var result = getIslandViaLocation.invoke(api, location);
                if (result instanceof Optional<?> opt && opt.isPresent()) {
                    Object island = opt.get();
                    var getId = island.getClass().getMethod("getId");
                    Object id = getId.invoke(island);
                    return id != null ? id.toString() : null;
                }
            } catch (Exception ignored) {
            }
        }

        return player.getWorld() != null ? player.getWorld().getName() : DEFAULT_ISLAND_KEY;
    }

    private boolean isIslandMember(Player player, Location location) {
        Plugin iridium = Bukkit.getPluginManager().getPlugin("IridiumSkyblock");
        if (iridium != null && iridium.isEnabled()) {
            try {
                Class<?> apiClass = Class.forName("com.iridium.iridiumskyblock.api.IridiumSkyblockAPI");
                var getInstance = apiClass.getMethod("getInstance");
                var api = getInstance.invoke(null);

                var getIslandViaLocation = apiClass.getMethod("getIslandViaLocation", Location.class);
                var result = getIslandViaLocation.invoke(api, location);
                if (result instanceof Optional<?> opt && opt.isPresent()) {
                    Object island = opt.get();
                    UUID playerId = player.getUniqueId();

                    if (isIslandOwner(island, playerId)) {
                        return true;
                    }

                    Object membersObj = invokeNoArg(island, "getMembers", "getMemberList", "getIslandMembers");
                    if (membersObj instanceof java.util.Map<?, ?> map) {
                        return map.keySet().stream().anyMatch(entry -> isMatchingMember(entry, playerId));
                    }
                    if (membersObj instanceof Iterable<?> members) {
                        for (Object member : members) {
                            if (isMatchingMember(member, playerId)) {
                                return true;
                            }
                        }
                    }

                    return false;
                }
            } catch (Exception ignored) {
            }

            return false;
        }

        return true;
    }

    private boolean isIslandOwner(Object island, UUID playerId) {
        Object ownerObj = invokeNoArg(island, "getOwner", "getOwnerUuid", "getOwnerUUID", "getLeader", "getOwnerId");
        return isMatchingMember(ownerObj, playerId);
    }

    private boolean isMatchingMember(Object member, UUID playerId) {
        if (member == null) return false;
        if (member instanceof UUID uuid) {
            return uuid.equals(playerId);
        }
        if (member instanceof org.bukkit.OfflinePlayer offlinePlayer) {
            return playerId.equals(offlinePlayer.getUniqueId());
        }
        Object uuidObj = invokeNoArg(member, "getUuid", "getUUID", "getUniqueId", "getPlayerUuid", "getPlayerId");
        if (uuidObj instanceof UUID uuid) {
            return uuid.equals(playerId);
        }
        if (uuidObj instanceof String uuidString) {
            try {
                return UUID.fromString(uuidString).equals(playerId);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private Object invokeNoArg(Object target, String... methodNames) {
        if (target == null) return null;
        for (String methodName : methodNames) {
            try {
                var method = target.getClass().getMethod(methodName);
                method.setAccessible(true);
                return method.invoke(target);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private boolean isValidChestType(Material material) {
        return material == Material.CHEST
                || material == Material.TRAPPED_CHEST
                || material == Material.BARREL
                || material.name().contains("SHULKER_BOX");
    }

    private String formatItemName(Material material) {
        String name = material.name().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }
}
