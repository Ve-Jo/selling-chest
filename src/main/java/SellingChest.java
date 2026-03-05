import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class SellingChest {

    private final UUID id;
    private final Location location;
    private final String islandKey;
    private final UUID ownerUuid;
    private Material sellingItem;
    private double pricePerItem;
    private String displayName;
    private boolean enabled;

    public SellingChest(UUID id, Location location, String islandKey, UUID ownerUuid) {
        this.id = id;
        this.location = location;
        this.islandKey = islandKey;
        this.ownerUuid = ownerUuid;
        this.sellingItem = null;
        this.pricePerItem = 0.0;
        this.displayName = null;
        this.enabled = false;
    }

    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getIslandKey() {
        return islandKey;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public Material getSellingItem() {
        return sellingItem;
    }

    public void setSellingItem(Material sellingItem) {
        this.sellingItem = sellingItem;
    }

    public double getPricePerItem() {
        return pricePerItem;
    }

    public void setPricePerItem(double pricePerItem) {
        this.pricePerItem = Math.max(0.0, pricePerItem);
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConfigured() {
        return sellingItem != null && pricePerItem > 0.0;
    }

    public String getHologramName() {
        return "sellchest_" + id.toString().replace("-", "").substring(0, 16);
    }

    public int getStockInChest() {
        if (location.getWorld() == null || sellingItem == null) {
            return 0;
        }
        if (!(location.getBlock().getState() instanceof org.bukkit.block.Container container)) {
            return 0;
        }
        int count = 0;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && item.getType() == sellingItem) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public boolean removeItems(int amount) {
        if (location.getWorld() == null || sellingItem == null || amount <= 0) {
            return false;
        }
        if (!(location.getBlock().getState() instanceof org.bukkit.block.Container container)) {
            return false;
        }
        int remaining = amount;
        for (ItemStack item : container.getInventory().getContents()) {
            if (item != null && item.getType() == sellingItem && remaining > 0) {
                int toRemove = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - toRemove);
                remaining -= toRemove;
            }
        }
        return remaining == 0;
    }
}
