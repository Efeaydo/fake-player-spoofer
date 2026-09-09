package me.fakeplayerspoofer.listeners;

import me.fakeplayerspoofer.FakePlayerSpooferPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerJoinQuitListener implements Listener {

    private final FakePlayerSpooferPlugin plugin;

    public PlayerJoinQuitListener(FakePlayerSpooferPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Send Tab fake players after a brief 10 tick delay so client's initial login sequence is settled
        new BukkitRunnable() {
            @Override
            public void run() {
                if (event.getPlayer().isOnline() && plugin.getTabListManager() != null) {
                    plugin.getTabListManager().sendUpdateToPlayer(event.getPlayer());
                }
            }
        }.runTaskLater(plugin, 10L);
    }
}
