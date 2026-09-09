package me.fakeplayerspoofer.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import me.fakeplayerspoofer.FakePlayerSpooferPlugin;
import me.fakeplayerspoofer.managers.SpooferManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.List;

public class PingListener implements Listener {

    private final FakePlayerSpooferPlugin plugin;

    public PingListener(FakePlayerSpooferPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onServerListPing(PaperServerListPingEvent event) {
        SpooferManager manager = plugin.getSpooferManager();
        if (manager == null || !manager.isEnabled()) return;

        int spoofedOnline = manager.getSpoofedOnlineCount();
        int spoofedMax = manager.getSpoofedMaxPlayers();

        event.setNumPlayers(spoofedOnline);
        event.setMaxPlayers(spoofedMax);

        List<PlayerProfile> sample = manager.generateHoverProfiles();
        if (!sample.isEmpty()) {
            event.getPlayerSample().clear();
            event.getPlayerSample().addAll(sample);
        }
    }
}
