package net.teujaem.spparty.listener;

import net.teujaem.spparty.Main;
import net.teujaem.spparty.service.PartyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerListener implements Listener {
    private final Main plugin;
    private final PartyService service;
    public PlayerListener(Main plugin, PartyService service) { this.plugin = plugin; this.service = service; }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> service.notifyPending(event.getPlayer()), 20L);
        plugin.getServer().getScheduler().runTaskLater(plugin, service::requestSnapshot, 40L);
    }
}
