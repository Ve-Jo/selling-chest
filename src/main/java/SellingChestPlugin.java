import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class SellingChestPlugin extends JavaPlugin {

    private SellingChestManager sellingChestManager;
    private SellingChestListener sellingChestListener;
    private SellingChestCommand sellingChestCommand;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        sellingChestManager = new SellingChestManager(this);
        sellingChestCommand = new SellingChestCommand(this, sellingChestManager);
        getCommand("sellchest").setExecutor(sellingChestCommand);
        getCommand("sellchest").setTabCompleter(sellingChestCommand);

        sellingChestListener = new SellingChestListener(this, sellingChestManager, sellingChestCommand.getWandKey());
        Bukkit.getPluginManager().registerEvents(sellingChestListener, this);
    }

    @Override
    public void onDisable() {
        if (sellingChestListener != null) {
            sellingChestListener.stop();
        }
    }
}
