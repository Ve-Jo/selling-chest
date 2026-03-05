import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class SellingChestCommand implements TabExecutor {

    private final JavaPlugin plugin;
    private final SellingChestManager chestManager;
    private final NamespacedKey wandKey;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public SellingChestCommand(JavaPlugin plugin, SellingChestManager chestManager) {
        this.plugin = plugin;
        this.chestManager = chestManager;
        this.wandKey = new NamespacedKey(plugin, "sellchest_wand");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cЭту команду могут использовать только игроки.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("market")) {
            if (!plugin.getConfig().getBoolean("selling-chests.auction-menu.enabled", true)) {
                player.sendMessage("§cРынок торговых сундуков отключен.");
                return true;
            }

            var rsp = Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
            net.milkbowl.vault.economy.Economy economy = rsp != null ? rsp.getProvider() : null;
            if (economy == null) {
                player.sendMessage("§cЭкономика недоступна. Обратитесь к администратору.");
                return true;
            }

            new SellingChestAuctionMenu(plugin, player, chestManager, economy, 0).open();
            return true;
        }

        giveWand(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("market");
        }
        return List.of();
    }

    private void giveWand(Player player) {
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(miniMessage.deserialize("<gradient:#FFAA00:#FF5500><bold>Палочка торгового сундука"));
            List<Component> lore = new ArrayList<>();
            lore.add(miniMessage.deserialize("<gray>ПКМ по сундуку чтобы создать торговый сундук"));
            lore.add(miniMessage.deserialize("<gray>Шифт + ПКМ чтобы удалить"));
            lore.add(miniMessage.deserialize("<gray>После создания, ПКМ для открытия меню"));
            meta.lore(lore);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        player.sendMessage("§aВы получили §6Палочку торгового сундука§a!");
        player.sendMessage("§7ПКМ по любому сундуку чтобы превратить его в торговый.");
        player.sendMessage("§7После создания, §eПКМ по сундуку§7 для открытия меню настройки.");
        player.sendMessage("§7Держите §cШИФТ§7 и нажмите ПКМ чтобы удалить торговый сундук.");
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }
}
