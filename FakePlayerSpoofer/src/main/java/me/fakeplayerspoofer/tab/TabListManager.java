package me.fakeplayerspoofer.tab;

import com.mojang.authlib.GameProfile;
import me.fakeplayerspoofer.FakePlayerSpooferPlugin;
import me.fakeplayerspoofer.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class TabListManager {

    private final FakePlayerSpooferPlugin plugin;
    private boolean enabled;
    private String mode;
    private int count;
    private int fakePing;
    private String gamemode;
    private String nameFormat;
    private int refreshInterval;

    private final List<UUID> currentActiveFakeUuids = new ArrayList<>();
    private final List<String> currentActiveFakeNames = new ArrayList<>();
    private BukkitTask refreshTask;
    private boolean reflectionSupported = true;

    // Cached Reflection Elements
    private Class<?> packetUpdateClass;
    private Class<?> packetRemoveClass;
    private Class<?> actionEnumClass;
    private Class<?> entryClass;
    private Class<?> gameTypeClass;
    private Object gameTypeConstant;
    private Constructor<?> entryConstructor;
    private Constructor<?> packetUpdateConstructor;
    private Constructor<?> packetRemoveConstructor;

    public TabListManager(FakePlayerSpooferPlugin plugin) {
        this.plugin = plugin;
        initReflection();
        loadConfig();
        startTask();
    }

    private void initReflection() {
        try {
            packetUpdateClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket");
            packetRemoveClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket");
            actionEnumClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Action");
            entryClass = Class.forName("net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket$Entry");
            gameTypeClass = Class.forName("net.minecraft.world.level.GameType");

            // GameType SURVIVAL
            for (Object constant : gameTypeClass.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase("SURVIVAL")) {
                    gameTypeConstant = constant;
                    break;
                }
            }
            if (gameTypeConstant == null && gameTypeClass.getEnumConstants().length > 0) {
                gameTypeConstant = gameTypeClass.getEnumConstants()[0];
            }

            // Find Entry constructor
            for (Constructor<?> c : entryClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] params = c.getParameterTypes();
                if (params.length >= 5) {
                    entryConstructor = c;
                    break;
                }
            }

            // Find Packet constructors
            for (Constructor<?> c : packetUpdateClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 2 && Collection.class.isAssignableFrom(params[1])) {
                    packetUpdateConstructor = c;
                    break;
                }
            }

            for (Constructor<?> c : packetRemoveClass.getDeclaredConstructors()) {
                c.setAccessible(true);
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 1 && List.class.isAssignableFrom(params[0])) {
                    packetRemoveConstructor = c;
                    break;
                }
            }

            reflectionSupported = (entryConstructor != null && (packetUpdateConstructor != null || canSetFieldsDirectly()));
        } catch (Throwable t) {
            reflectionSupported = false;
            plugin.getLogger().warning("In-game Tab list packet injection is not directly supported on this server build: " + t.getMessage());
        }
    }

    private boolean canSetFieldsDirectly() {
        try {
            Field f = packetUpdateClass.getDeclaredField("entries");
            return f != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("tab-list.enabled", true);
        this.mode = config.getString("tab-list.mode", "FIXED");
        this.count = config.getInt("tab-list.count", 12);
        this.fakePing = config.getInt("tab-list.fake-ping", 28);
        this.gamemode = config.getString("tab-list.gamemode", "SURVIVAL");
        this.nameFormat = config.getString("tab-list.name-format", "&7%name%");
        this.refreshInterval = config.getInt("tab-list.refresh-interval-seconds", 30);

        if (enabled && reflectionSupported) {
            updateActiveFakePlayers();
            sendUpdateToAll();
        } else {
            clearTabListForAll();
        }
    }

    private void startTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }

        long ticks = Math.max(10, refreshInterval) * 20L;
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || !reflectionSupported) return;
                updateActiveFakePlayers();
                sendUpdateToAll();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    public void updateActiveFakePlayers() {
        List<String> pool = plugin.getConfig().getStringList("fake-names-pool");
        if (pool == null || pool.isEmpty()) {
            pool = Arrays.asList("Viper", "ShadowNinja", "NightWolf", "Phoenix_99", "SkyWalker", "GhostRider");
        }

        int targetCount = count;
        if ("SYNC".equalsIgnoreCase(mode)) {
            targetCount = plugin.getSpooferManager().getRawFakeCount();
        }
        targetCount = Math.max(1, Math.min(pool.size(), targetCount));

        // Save old UUIDs to remove if changed
        List<UUID> oldUuids = new ArrayList<>(currentActiveFakeUuids);

        currentActiveFakeNames.clear();
        currentActiveFakeUuids.clear();

        List<String> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);

        for (int i = 0; i < targetCount; i++) {
            String name = shuffled.get(i);
            currentActiveFakeNames.add(name);
            UUID fakeUuid = UUID.nameUUIDFromBytes(("fake_tab_" + name).getBytes(StandardCharsets.UTF_8));
            currentActiveFakeUuids.add(fakeUuid);
        }

        // If old UUIDs exist and differ, remove old ones first
        if (!oldUuids.isEmpty()) {
            sendRemoveToAll(oldUuids);
        }
    }

    public void sendUpdateToPlayer(Player player) {
        if (!enabled || !reflectionSupported || currentActiveFakeUuids.isEmpty()) return;

        try {
            Object packet = createAddPlayerPacket();
            if (packet != null) {
                sendPacket(player, packet);
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("Could not send tab packet to " + player.getName() + ": " + t.getMessage());
        }
    }

    public void sendUpdateToAll() {
        if (!enabled || !reflectionSupported || currentActiveFakeUuids.isEmpty()) return;

        try {
            Object packet = createAddPlayerPacket();
            if (packet != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendPacket(player, packet);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().fine("Could not broadcast tab packet: " + t.getMessage());
        }
    }

    public void clearTabListForPlayer(Player player) {
        if (!reflectionSupported || currentActiveFakeUuids.isEmpty()) return;
        sendRemoveToPlayer(player, currentActiveFakeUuids);
    }

    public void clearTabListForAll() {
        if (!reflectionSupported || currentActiveFakeUuids.isEmpty()) return;
        sendRemoveToAll(currentActiveFakeUuids);
    }

    private void sendRemoveToPlayer(Player player, List<UUID> uuids) {
        if (!reflectionSupported || uuids.isEmpty()) return;
        try {
            Object packet = createRemovePacket(uuids);
            if (packet != null) {
                sendPacket(player, packet);
            }
        } catch (Throwable ignored) {}
    }

    private void sendRemoveToAll(List<UUID> uuids) {
        if (!reflectionSupported || uuids.isEmpty()) return;
        try {
            Object packet = createRemovePacket(uuids);
            if (packet != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    sendPacket(player, packet);
                }
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object createAddPlayerPacket() throws Exception {
        EnumSet actions = EnumSet.allOf((Class<Enum>) actionEnumClass);
        List<Object> entries = new ArrayList<>();

        for (int i = 0; i < currentActiveFakeUuids.size(); i++) {
            UUID uuid = currentActiveFakeUuids.get(i);
            String name = currentActiveFakeNames.get(i);
            GameProfile profile = new GameProfile(uuid, name);

            Object entry = createEntry(uuid, profile, true, fakePing, gameTypeConstant);
            if (entry != null) {
                entries.add(entry);
            }
        }

        if (packetUpdateConstructor != null) {
            return packetUpdateConstructor.newInstance(actions, entries);
        } else {
            // Allocate and set fields
            Object packet = allocateInstance(packetUpdateClass);
            setField(packet, "actions", actions);
            setField(packet, "entries", entries);
            return packet;
        }
    }

    private Object createRemovePacket(List<UUID> uuids) throws Exception {
        if (packetRemoveConstructor != null) {
            return packetRemoveConstructor.newInstance(uuids);
        } else {
            Object packet = allocateInstance(packetRemoveClass);
            setField(packet, "profileIds", uuids);
            return packet;
        }
    }

    private Object createEntry(UUID uuid, GameProfile profile, boolean listed, int latency, Object gameType) {
        try {
            Class<?>[] paramTypes = entryConstructor.getParameterTypes();
            Object[] args = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> type = paramTypes[i];
                if (UUID.class.isAssignableFrom(type)) {
                    args[i] = uuid;
                } else if (GameProfile.class.isAssignableFrom(type)) {
                    args[i] = profile;
                } else if (type == boolean.class || type == Boolean.class) {
                    args[i] = listed;
                } else if (type == int.class || type == Integer.class) {
                    args[i] = latency;
                } else if (gameTypeClass.isAssignableFrom(type)) {
                    args[i] = gameType;
                } else {
                    args[i] = null; // Component, chatSession, etc.
                }
            }

            return entryConstructor.newInstance(args);
        } catch (Throwable t) {
            return null;
        }
    }

    private void sendPacket(Player player, Object packet) {
        try {
            Method getHandleMethod = player.getClass().getMethod("getHandle");
            Object serverPlayer = getHandleMethod.invoke(player);

            Field connectionField = null;
            try {
                connectionField = serverPlayer.getClass().getField("connection");
            } catch (NoSuchFieldException e) {
                for (Field f : serverPlayer.getClass().getFields()) {
                    if (f.getType().getName().contains("ServerGamePacketListenerImpl")) {
                        connectionField = f;
                        break;
                    }
                }
            }

            if (connectionField == null) return;
            Object connection = connectionField.get(serverPlayer);

            Method sendMethod = null;
            for (Method m : connection.getClass().getMethods()) {
                if (m.getName().equals("send") && m.getParameterCount() == 1) {
                    sendMethod = m;
                    break;
                }
            }

            if (sendMethod != null) {
                sendMethod.invoke(connection, packet);
            }
        } catch (Throwable ignored) {}
    }

    private Object allocateInstance(Class<?> clazz) throws Exception {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method allocateInstance = unsafe.getClass().getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, clazz);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            f.set(target, value);
        } catch (Throwable ignored) {}
    }

    public void cleanup() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        clearTabListForAll();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("tab-list.enabled", enabled);
        plugin.saveConfig();
        if (enabled) {
            updateActiveFakePlayers();
            sendUpdateToAll();
        } else {
            clearTabListForAll();
        }
    }

    public int getTabCount() {
        return currentActiveFakeUuids.size();
    }

    public List<String> getCurrentActiveFakeNames() {
        return Collections.unmodifiableList(currentActiveFakeNames);
    }
}
