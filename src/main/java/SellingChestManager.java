import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SellingChestManager {

    private final JavaPlugin plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Map<UUID, SellingChest> chestsById = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> chestsByIsland = new ConcurrentHashMap<>();
    private final Map<Location, UUID> chestsByLocation = new ConcurrentHashMap<>();

    public SellingChestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "sellingchests.yml");
        loadData();
    }

    public SellingChest createChest(Location location, String islandKey, UUID ownerUuid) {
        if (chestsByLocation.containsKey(location)) {
            return null;
        }
        UUID id = UUID.randomUUID();
        SellingChest chest = new SellingChest(id, location.clone(), islandKey, ownerUuid);
        chestsById.put(id, chest);
        chestsByLocation.put(location, id);
        chestsByIsland.computeIfAbsent(islandKey, k -> ConcurrentHashMap.newKeySet()).add(id);
        saveData();
        return chest;
    }

    public SellingChest getChest(UUID id) {
        return chestsById.get(id);
    }

    public SellingChest getChestAt(Location location) {
        UUID id = chestsByLocation.get(location);
        if (id != null) {
            return chestsById.get(id);
        }

        Location otherHalf = getDoubleChestOtherHalf(location);
        if (otherHalf != null) {
            id = chestsByLocation.get(otherHalf);
            if (id != null) {
                return chestsById.get(id);
            }
        }

        return null;
    }

    private Location getDoubleChestOtherHalf(Location loc) {
        if (loc.getWorld() == null) return null;
        var block = loc.getBlock();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return null;
        }

        org.bukkit.block.data.type.Chest chestData = (org.bukkit.block.data.type.Chest) block.getBlockData();
        org.bukkit.block.data.type.Chest.Type chestType = chestData.getType();

        if (chestType == org.bukkit.block.data.type.Chest.Type.SINGLE) {
            return null;
        }

        org.bukkit.block.BlockFace facing = chestData.getFacing();
        org.bukkit.block.BlockFace otherHalfFace;

        if (chestType == org.bukkit.block.data.type.Chest.Type.LEFT) {
            otherHalfFace = getRightFace(facing);
        } else {
            otherHalfFace = getLeftFace(facing);
        }

        return block.getRelative(otherHalfFace).getLocation();
    }

    private org.bukkit.block.BlockFace getRightFace(org.bukkit.block.BlockFace facing) {
        return switch (facing) {
            case NORTH -> org.bukkit.block.BlockFace.EAST;
            case SOUTH -> org.bukkit.block.BlockFace.WEST;
            case EAST -> org.bukkit.block.BlockFace.SOUTH;
            case WEST -> org.bukkit.block.BlockFace.NORTH;
            default -> org.bukkit.block.BlockFace.NORTH;
        };
    }

    private org.bukkit.block.BlockFace getLeftFace(org.bukkit.block.BlockFace facing) {
        return switch (facing) {
            case NORTH -> org.bukkit.block.BlockFace.WEST;
            case SOUTH -> org.bukkit.block.BlockFace.EAST;
            case EAST -> org.bukkit.block.BlockFace.NORTH;
            case WEST -> org.bukkit.block.BlockFace.SOUTH;
            default -> org.bukkit.block.BlockFace.NORTH;
        };
    }

    public List<SellingChest> getChestsByIsland(String islandKey) {
        Set<UUID> ids = chestsByIsland.get(islandKey);
        if (ids == null) {
            return List.of();
        }
        List<SellingChest> result = new ArrayList<>();
        for (UUID id : ids) {
            SellingChest chest = chestsById.get(id);
            if (chest != null) {
                result.add(chest);
            }
        }
        return result;
    }

    public List<SellingChest> getChestsByOwner(UUID ownerUuid) {
        List<SellingChest> result = new ArrayList<>();
        for (SellingChest chest : chestsById.values()) {
            if (chest.getOwnerUuid().equals(ownerUuid)) {
                result.add(chest);
            }
        }
        return result;
    }

    public boolean removeChest(UUID id) {
        SellingChest chest = chestsById.remove(id);
        if (chest == null) {
            return false;
        }
        chestsByLocation.remove(chest.getLocation());
        Set<UUID> islandChests = chestsByIsland.get(chest.getIslandKey());
        if (islandChests != null) {
            islandChests.remove(id);
            if (islandChests.isEmpty()) {
                chestsByIsland.remove(chest.getIslandKey());
            }
        }
        saveData();
        return true;
    }

    public boolean removeChestAt(Location location) {
        UUID id = chestsByLocation.get(location);
        if (id != null) {
            return removeChest(id);
        }

        Location otherHalf = getDoubleChestOtherHalf(location);
        if (otherHalf != null) {
            id = chestsByLocation.get(otherHalf);
            if (id != null) {
                return removeChest(id);
            }
        }

        return false;
    }

    public Collection<SellingChest> getAllChests() {
        return Collections.unmodifiableCollection(chestsById.values());
    }

    public void saveChest(SellingChest chest) {
        saveData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            return;
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection chestsSection = dataConfig.getConfigurationSection("chests");
        if (chestsSection == null) {
            return;
        }
        for (String idStr : chestsSection.getKeys(false)) {
            try {
                UUID id = UUID.fromString(idStr);
                ConfigurationSection section = chestsSection.getConfigurationSection(idStr);
                if (section == null) continue;

                Location location = loadLocation(section.getConfigurationSection("location"));
                if (location == null) continue;

                String islandKey = section.getString("islandKey");
                UUID ownerUuid = UUID.fromString(Objects.requireNonNull(section.getString("ownerUuid")));

                SellingChest chest = new SellingChest(id, location, islandKey, ownerUuid);

                String itemStr = section.getString("sellingItem");
                if (itemStr != null) {
                    try {
                        chest.setSellingItem(Material.valueOf(itemStr));
                    } catch (IllegalArgumentException ignored) {
                    }
                }

                chest.setPricePerItem(section.getDouble("pricePerItem", 0.0));
                chest.setDisplayName(section.getString("displayName"));
                chest.setEnabled(section.getBoolean("enabled", false));

                chestsById.put(id, chest);
                chestsByLocation.put(location, id);
                chestsByIsland.computeIfAbsent(islandKey, k -> ConcurrentHashMap.newKeySet()).add(id);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load selling chest: " + idStr + " - " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + chestsById.size() + " selling chests.");
    }

    private void saveData() {
        dataConfig = new YamlConfiguration();
        ConfigurationSection chestsSection = dataConfig.createSection("chests");

        for (SellingChest chest : chestsById.values()) {
            ConfigurationSection section = chestsSection.createSection(chest.getId().toString());
            section.set("location", saveLocation(chest.getLocation()));
            section.set("islandKey", chest.getIslandKey());
            section.set("ownerUuid", chest.getOwnerUuid().toString());
            section.set("sellingItem", chest.getSellingItem() != null ? chest.getSellingItem().name() : null);
            section.set("pricePerItem", chest.getPricePerItem());
            section.set("displayName", chest.getDisplayName());
            section.set("enabled", chest.isEnabled());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save selling chests data: " + e.getMessage());
        }
    }

    private Location loadLocation(ConfigurationSection section) {
        if (section == null) return null;
        World world = Bukkit.getWorld(Objects.requireNonNull(section.getString("world")));
        if (world == null) return null;
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        return new Location(world, x, y, z);
    }

    private Map<String, Object> saveLocation(Location location) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        return map;
    }

    public void cleanupInvalidChests() {
        List<UUID> toRemove = new ArrayList<>();
        for (SellingChest chest : chestsById.values()) {
            Location loc = chest.getLocation();
            if (loc.getWorld() == null) {
                toRemove.add(chest.getId());
                continue;
            }
            Material type = loc.getBlock().getType();
            if (!isValidChestType(type)) {
                toRemove.add(chest.getId());
            }
        }
        for (UUID id : toRemove) {
            removeChest(id);
            plugin.getLogger().info("Removed invalid selling chest: " + id);
        }
        if (!toRemove.isEmpty()) {
            saveData();
        }
    }

    private boolean isValidChestType(Material material) {
        return material == Material.CHEST
                || material == Material.TRAPPED_CHEST
                || material == Material.BARREL
                || material.name().contains("SHULKER_BOX");
    }
}
