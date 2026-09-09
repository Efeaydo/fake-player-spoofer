package me.fakeplayerspoofer.managers;

import com.destroystokyo.paper.profile.PlayerProfile;
import me.fakeplayerspoofer.FakePlayerSpooferPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class SpooferManager {

    public enum Mode {
        DYNAMIC_247,
        STATIC,
        MULTIPLIER
    }

    private final FakePlayerSpooferPlugin plugin;
    private boolean enabled;
    private Mode mode = Mode.DYNAMIC_247;
    private boolean includeRealPlayers;
    private int staticCount;
    private double multiplier;

    private int lateNightMin, lateNightMax;
    private int morningMin, morningMax;
    private int afternoonMin, afternoonMax;
    private int eveningMin, eveningMax;
    private int nightMin, nightMax;

    private int fluctuationInterval;
    private int fluctuationDelta;

    private boolean spoofMax;
    private int dynamicBuffer;
    private int fixedMax;

    private boolean hoverSampleEnabled;
    private int sampleCount;
    private List<String> fakeNamesPool;

    private int currentDynamicFakeCount = 20;
    private BukkitTask fluctuationTask;

    public SpooferManager(FakePlayerSpooferPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startFluctuationTask();
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        this.enabled = config.getBoolean("enabled", true);
        String modeStr = config.getString("mode", "DYNAMIC_247");
        try {
            this.mode = Mode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            this.mode = Mode.DYNAMIC_247;
        }

        this.includeRealPlayers = config.getBoolean("include-real-players", true);
        this.staticCount = config.getInt("static-count", 25);
        this.multiplier = config.getDouble("multiplier", 2.0);

        this.lateNightMin = config.getInt("dynamic-curve.late-night.min", 8);
        this.lateNightMax = config.getInt("dynamic-curve.late-night.max", 14);

        this.morningMin = config.getInt("dynamic-curve.morning.min", 15);
        this.morningMax = config.getInt("dynamic-curve.morning.max", 24);

        this.afternoonMin = config.getInt("dynamic-curve.afternoon.min", 25);
        this.afternoonMax = config.getInt("dynamic-curve.afternoon.max", 38);

        this.eveningMin = config.getInt("dynamic-curve.evening-peak.min", 38);
        this.eveningMax = config.getInt("dynamic-curve.evening-peak.max", 56);

        this.nightMin = config.getInt("dynamic-curve.night.min", 20);
        this.nightMax = config.getInt("dynamic-curve.night.max", 30);

        this.fluctuationInterval = config.getInt("dynamic-curve.fluctuation-interval-seconds", 45);
        this.fluctuationDelta = config.getInt("dynamic-curve.fluctuation-delta", 3);

        this.spoofMax = config.getBoolean("max-players.spoof-max", true);
        this.dynamicBuffer = config.getInt("max-players.dynamic-buffer", 1);
        this.fixedMax = config.getInt("max-players.fixed-max", 100);

        this.hoverSampleEnabled = config.getBoolean("hover-sample.enabled", true);
        this.sampleCount = config.getInt("hover-sample.sample-count", 10);
        this.fakeNamesPool = config.getStringList("fake-names-pool");
        if (this.fakeNamesPool == null || this.fakeNamesPool.isEmpty()) {
            this.fakeNamesPool = Arrays.asList("Viper", "ShadowNinja", "NightWolf", "Phoenix_99", "SkyWalker", "GhostRider");
        }

        calculateInitialCount();
    }

    private void calculateInitialCount() {
        this.currentDynamicFakeCount = getTargetForCurrentHour();
    }

    private int getTargetForCurrentHour() {
        LocalTime now = LocalTime.now();
        int hour = now.getHour();

        int min, max;
        if (hour >= 2 && hour < 7) {
            min = lateNightMin;
            max = lateNightMax;
        } else if (hour >= 7 && hour < 12) {
            min = morningMin;
            max = morningMax;
        } else if (hour >= 12 && hour < 18) {
            min = afternoonMin;
            max = afternoonMax;
        } else if (hour >= 18 && hour < 23) {
            min = eveningMin;
            max = eveningMax;
        } else {
            min = nightMin;
            max = nightMax;
        }

        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void startFluctuationTask() {
        if (fluctuationTask != null) {
            fluctuationTask.cancel();
        }

        long intervalTicks = Math.max(10, fluctuationInterval) * 20L;
        fluctuationTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || mode != Mode.DYNAMIC_247) return;

                int target = getTargetForCurrentHour();
                int diff = target - currentDynamicFakeCount;

                int delta = ThreadLocalRandom.current().nextInt(-fluctuationDelta, fluctuationDelta + 1);

                if (diff > 5) {
                    currentDynamicFakeCount += Math.max(1, Math.abs(delta));
                } else if (diff < -5) {
                    currentDynamicFakeCount -= Math.max(1, Math.abs(delta));
                } else {
                    currentDynamicFakeCount += delta;
                }

                if (currentDynamicFakeCount < 1) currentDynamicFakeCount = 1;
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);
    }

    public int getSpoofedOnlineCount() {
        int real = Bukkit.getOnlinePlayers().size();
        if (!enabled) return real;

        int fakeCount;
        switch (mode) {
            case STATIC:
                fakeCount = staticCount;
                break;
            case MULTIPLIER:
                fakeCount = (int) Math.round(real * multiplier);
                break;
            case DYNAMIC_247:
            default:
                fakeCount = currentDynamicFakeCount;
                break;
        }

        if (includeRealPlayers) {
            return fakeCount + real;
        }
        return Math.max(real, fakeCount);
    }

    public int getSpoofedMaxPlayers() {
        if (!enabled || !spoofMax) {
            return Bukkit.getMaxPlayers();
        }

        int online = getSpoofedOnlineCount();
        if (dynamicBuffer > 0) {
            return online + dynamicBuffer;
        }
        return fixedMax;
    }

    public List<PlayerProfile> generateHoverProfiles() {
        List<PlayerProfile> list = new ArrayList<>();
        if (!enabled || !hoverSampleEnabled) return list;

        List<String> poolCopy = new ArrayList<>(fakeNamesPool);
        Collections.shuffle(poolCopy);

        int count = Math.min(sampleCount, poolCopy.size());
        for (int i = 0; i < count; i++) {
            String name = poolCopy.get(i);
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), name);
            list.add(profile);
        }
        return list;
    }

    public void cleanup() {
        if (fluctuationTask != null) {
            fluctuationTask.cancel();
            fluctuationTask = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("enabled", enabled);
        plugin.saveConfig();
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        plugin.getConfig().set("mode", mode.name());
        plugin.saveConfig();
    }

    public int getStaticCount() {
        return staticCount;
    }

    public void setStaticCount(int staticCount) {
        this.staticCount = staticCount;
        plugin.getConfig().set("static-count", staticCount);
        plugin.saveConfig();
    }

    public int getRawFakeCount() {
        switch (mode) {
            case STATIC: return staticCount;
            case MULTIPLIER: return (int) Math.round(Bukkit.getOnlinePlayers().size() * multiplier);
            case DYNAMIC_247:
            default: return currentDynamicFakeCount;
        }
    }
}
