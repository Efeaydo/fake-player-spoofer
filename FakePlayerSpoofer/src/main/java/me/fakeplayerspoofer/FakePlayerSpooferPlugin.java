package me.fakeplayerspoofer;

import me.fakeplayerspoofer.commands.FakePlayerCommand;
import me.fakeplayerspoofer.hooks.PlaceholderHook;
import me.fakeplayerspoofer.listeners.PingListener;
import me.fakeplayerspoofer.listeners.PlayerJoinQuitListener;
import me.fakeplayerspoofer.managers.SpooferManager;
import me.fakeplayerspoofer.tab.TabListManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class FakePlayerSpooferPlugin extends JavaPlugin {

    private static FakePlayerSpooferPlugin instance;
    private SpooferManager spooferManager;
    private TabListManager tabListManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        this.spooferManager = new SpooferManager(this);
        this.tabListManager = new TabListManager(this);

        // Register Event Listeners
        getServer().getPluginManager().registerEvents(new PingListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

        // Register Command
        FakePlayerCommand command = new FakePlayerCommand(this);
        if (getCommand("fakeplayerspoofer") != null) {
            getCommand("fakeplayerspoofer").setExecutor(command);
            getCommand("fakeplayerspoofer").setTabCompleter(command);
        }

        // Register PlaceholderAPI hook (registering both 'fakeplayerspoofer' and 'spoofer' identifiers)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this, "fakeplayerspoofer").register();
            new PlaceholderHook(this, "spoofer").register();
            getLogger().info("Successfully hooked into PlaceholderAPI with %fakeplayerspoofer_% and %spoofer_%!");
        }

        getLogger().info("FakePlayerSpoofer v" + getDescription().getVersion() + " has been enabled! (Server List + In-Game Tab List)");
    }

    @Override
    public void onDisable() {
        if (tabListManager != null) {
            tabListManager.cleanup();
        }
        if (spooferManager != null) {
            spooferManager.cleanup();
        }
        getLogger().info("FakePlayerSpoofer has been disabled.");
    }

    public static FakePlayerSpooferPlugin getInstance() {
        return instance;
    }

    public SpooferManager getSpooferManager() {
        return spooferManager;
    }

    public TabListManager getTabListManager() {
        return tabListManager;
    }
}
