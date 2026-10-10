package net.teujaem.spparty;

import net.teujaem.spFramework.SPFramework;
import net.teujaem.spparty.command.PartyCommand;
import net.teujaem.spparty.listener.PartyChatListener;
import net.teujaem.spparty.listener.PlayerListener;
import net.teujaem.spparty.service.PartyService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Main extends JavaPlugin {
    private static Main instance;
    private PartyService partyService;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        try {
            boolean proxyEnabled = getConfig().getBoolean("proxy.enabled", true)
                    && SPFramework.getInstance().getConfigManager().isProxy();
            boolean spProxyDataEnabled = Bukkit.getPluginManager().isPluginEnabled("SP-ProxyData");
            partyService = new PartyService(this, proxyEnabled, getConfig().getInt("party.max-size", 8));
            PartyCommand partyCommand = new PartyCommand(this, partyService);
            registerCommand("party", partyCommand);
            registerCommand("pc", partyCommand);
            Bukkit.getPluginManager().registerEvents(new PartyChatListener(this, partyService), this);
            Bukkit.getPluginManager().registerEvents(partyService, this);
            Bukkit.getPluginManager().registerEvents(new PlayerListener(this, partyService), this);
            Bukkit.getScheduler().runTaskLater(this, partyService::requestSnapshot, 20L);
            getLogger().info("SP-Party enabled (proxy=" + proxyEnabled
                    + ", SP-ProxyData=" + (spProxyDataEnabled ? "integrated" : "not installed") + ")");
        } catch (Exception | LinkageError exception) {
            getLogger().severe("SP-Party 초기화 실패: " + exception.getMessage());
            exception.printStackTrace();
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    private void registerCommand(String name, PartyCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("plugin.yml에 " + name + " 명령어가 없습니다.");
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public void onDisable() {
        if (partyService != null) partyService.shutdown();
        partyService = null;
        instance = null;
    }

    public void runSync(Runnable task) {
        if (isEnabled()) Bukkit.getScheduler().runTask(this, task);
    }

    public static Main getInstance() { return instance; }
}
