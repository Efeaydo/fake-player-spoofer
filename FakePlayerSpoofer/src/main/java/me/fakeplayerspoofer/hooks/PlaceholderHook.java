package me.fakeplayerspoofer.hooks;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.fakeplayerspoofer.FakePlayerSpooferPlugin;
import me.fakeplayerspoofer.managers.SpooferManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHook extends PlaceholderExpansion {

    private final FakePlayerSpooferPlugin plugin;
    private final String identifier;

    public PlaceholderHook(FakePlayerSpooferPlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public @NotNull String getIdentifier() {
        return identifier;
    }

    @Override
    public @NotNull String getAuthor() {
        return "DevTeam";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        SpooferManager manager = plugin.getSpooferManager();
        if (manager == null) return "";

        switch (params.toLowerCase()) {
            case "online":
            case "count":
                return String.valueOf(manager.getSpoofedOnlineCount());
            case "fake":
            case "fake_count":
                return String.valueOf(manager.getRawFakeCount());
            case "real":
            case "real_count":
                return String.valueOf(Bukkit.getOnlinePlayers().size());
            case "max":
            case "max_players":
                return String.valueOf(manager.getSpoofedMaxPlayers());
            case "mode":
                return manager.getMode().name();
            case "enabled":
                return String.valueOf(manager.isEnabled());
            case "tab_count":
                return plugin.getTabListManager() != null ? String.valueOf(plugin.getTabListManager().getTabCount()) : "0";
            case "tab_enabled":
                return plugin.getTabListManager() != null ? String.valueOf(plugin.getTabListManager().isEnabled()) : "false";
            default:
                return null;
        }
    }
}
