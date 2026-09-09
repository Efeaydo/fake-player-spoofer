package me.fakeplayerspoofer.commands;

import me.fakeplayerspoofer.FakePlayerSpooferPlugin;
import me.fakeplayerspoofer.managers.SpooferManager;
import me.fakeplayerspoofer.tab.TabListManager;
import me.fakeplayerspoofer.utils.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FakePlayerCommand implements CommandExecutor, TabCompleter {

    private final FakePlayerSpooferPlugin plugin;

    public FakePlayerCommand(FakePlayerSpooferPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("fakeplayerspoofer.admin")) {
            sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        SpooferManager spoofer = plugin.getSpooferManager();
        TabListManager tabList = plugin.getTabListManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender, spoofer, tabList);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "enable":
            case "on":
                spoofer.setEnabled(true);
                sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.enabled", "&aFakePlayerSpoofer has been enabled.")));
                return true;

            case "disable":
            case "off":
                spoofer.setEnabled(false);
                sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.disabled", "&cFakePlayerSpoofer has been disabled.")));
                return true;

            case "toggle":
                boolean newState = !spoofer.isEnabled();
                spoofer.setEnabled(newState);
                sender.sendMessage(MessageUtil.colorize(newState
                        ? plugin.getConfig().getString("messages.enabled", "&aFakePlayerSpoofer has been enabled.")
                        : plugin.getConfig().getString("messages.disabled", "&cFakePlayerSpoofer has been disabled.")));
                return true;

            case "tab":
                if (tabList == null) {
                    sender.sendMessage(MessageUtil.colorize("&cTabList manager is not available."));
                    return true;
                }
                if (args.length > 1) {
                    String tabAction = args[1].toLowerCase();
                    if (tabAction.equals("on") || tabAction.equals("enable")) {
                        tabList.setEnabled(true);
                    } else if (tabAction.equals("off") || tabAction.equals("disable")) {
                        tabList.setEnabled(false);
                    } else {
                        tabList.setEnabled(!tabList.isEnabled());
                    }
                } else {
                    tabList.setEnabled(!tabList.isEnabled());
                }
                String tabStatus = tabList.isEnabled() ? "&a&lENABLED" : "&c&lDISABLED";
                sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.tab-toggled", "&aIn-game TAB fake player list is now %status%&a.").replace("%status%", tabStatus)));
                return true;

            case "auto":
            case "dynamic":
                spoofer.setMode(SpooferManager.Mode.DYNAMIC_247);
                sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.mode-changed", "&aSpoofer mode set to &e%mode%&a.").replace("%mode%", "DYNAMIC_247")));
                if (tabList != null && tabList.isEnabled()) {
                    tabList.updateActiveFakePlayers();
                    tabList.sendUpdateToAll();
                }
                return true;

            case "mode":
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.colorize("&cUsage: /" + label + " mode <DYNAMIC_247|STATIC|MULTIPLIER>"));
                    return true;
                }
                try {
                    SpooferManager.Mode mode = SpooferManager.Mode.valueOf(args[1].toUpperCase());
                    spoofer.setMode(mode);
                    sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.mode-changed", "&aSpoofer mode set to &e%mode%&a.").replace("%mode%", mode.name())));
                    if (tabList != null && tabList.isEnabled()) {
                        tabList.updateActiveFakePlayers();
                        tabList.sendUpdateToAll();
                    }
                } catch (IllegalArgumentException e) {
                    sender.sendMessage(MessageUtil.colorize("&cInvalid mode! Choose: DYNAMIC_247, STATIC, MULTIPLIER"));
                }
                return true;

            case "set":
            case "count":
                if (args.length < 2) {
                    sender.sendMessage(MessageUtil.colorize("&cUsage: /" + label + " set <count>"));
                    return true;
                }
                try {
                    int count = Integer.parseInt(args[1]);
                    if (count < 0) count = 0;
                    spoofer.setStaticCount(count);
                    spoofer.setMode(SpooferManager.Mode.STATIC);
                    sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.count-set", "&aStatic fake player count set to &e%count%&a.").replace("%count%", String.valueOf(count))));
                    if (tabList != null && tabList.isEnabled()) {
                        tabList.updateActiveFakePlayers();
                        tabList.sendUpdateToAll();
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(MessageUtil.colorize("&cInvalid number: " + args[1]));
                }
                return true;

            case "reload":
                plugin.reloadConfig();
                spoofer.loadConfig();
                if (tabList != null) {
                    tabList.loadConfig();
                }
                sender.sendMessage(MessageUtil.colorize(plugin.getConfig().getString("messages.reloaded", "&aFakePlayerSpoofer configuration reloaded successfully.")));
                return true;

            case "help":
            default:
                sendHelp(sender, label);
                return true;
        }
    }

    private void sendStatus(CommandSender sender, SpooferManager spoofer, TabListManager tabList) {
        List<String> lines = plugin.getConfig().getStringList("messages.status");
        if (lines.isEmpty()) {
            sender.sendMessage(MessageUtil.colorize("&b&lFakePlayerSpoofer &7| Enabled: " + spoofer.isEnabled() + " | Mode: " + spoofer.getMode().name() + " | Spoofed: " + spoofer.getSpoofedOnlineCount()));
            return;
        }

        int real = Bukkit.getOnlinePlayers().size();
        int fake = spoofer.getSpoofedOnlineCount();
        int max = spoofer.getSpoofedMaxPlayers();
        String statusStr = spoofer.isEnabled() ? "&a&lENABLED" : "&c&lDISABLED";
        String tabStatusStr = (tabList != null && tabList.isEnabled()) ? "&a&lENABLED" : "&c&lDISABLED";
        int tabCount = tabList != null ? tabList.getTabCount() : 0;

        for (String line : lines) {
            String formatted = line
                    .replace("%status%", statusStr)
                    .replace("%mode%", spoofer.getMode().name())
                    .replace("%real%", String.valueOf(real))
                    .replace("%fake%", String.valueOf(fake))
                    .replace("%max%", String.valueOf(max))
                    .replace("%tab_status%", tabStatusStr)
                    .replace("%tab_count%", String.valueOf(tabCount));
            sender.sendMessage(MessageUtil.colorize(formatted));
        }
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(MessageUtil.colorize("&d&m----------------------------------------------------"));
        sender.sendMessage(MessageUtil.colorize("<#ff80bf>&l  FAKE PLAYER SPOOFER &7- &fCommands"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " status &7- View current spoofer & tab status"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " auto &7- Enable 24/7 Organic Dynamic curve"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " set <count> &7- Set fixed static fake player count"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " toggle &7- Toggle server list spoofer"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " tab [on|off] &7- Toggle in-game TAB fake player list"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " mode <mode> &7- Switch mode (DYNAMIC_247, STATIC, MULTIPLIER)"));
        sender.sendMessage(MessageUtil.colorize("&8 » &e/" + label + " reload &7- Reload config.yml"));
        sender.sendMessage(MessageUtil.colorize("&d&m----------------------------------------------------"));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> sub = Arrays.asList("status", "auto", "set", "toggle", "tab", "enable", "disable", "mode", "reload", "help");
            for (String s : sub) {
                if (s.toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(s);
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("mode")) {
            for (SpooferManager.Mode m : SpooferManager.Mode.values()) {
                if (m.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(m.name());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("tab")) {
            for (String opt : Arrays.asList("on", "off", "toggle")) {
                if (opt.startsWith(args[1].toLowerCase())) {
                    list.add(opt);
                }
            }
        }
        return list;
    }
}
