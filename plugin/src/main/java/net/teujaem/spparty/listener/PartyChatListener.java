package net.teujaem.spparty.listener;

import net.teujaem.spparty.Main;
import net.teujaem.spparty.service.PartyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChatEvent;

public final class PartyChatListener implements Listener {
    private static final String PREFIX = "§b[파티] §f";
    private final Main plugin;
    private final PartyService service;

    public PartyChatListener(Main plugin, PartyService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(PlayerChatEvent event) {
        if (!service.isPartyChatEnabled(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        try {
            service.chat(event.getPlayer(), event.getMessage());
        } catch (IllegalStateException exception) {
            service.setPartyChat(event.getPlayer(), false);
            event.getPlayer().sendMessage(PREFIX + "§c" + exception.getMessage() + " 전체 채팅 모드로 변경했습니다.");
        } catch (Exception | LinkageError exception) {
            plugin.getLogger().warning("파티 채팅 처리 실패: " + exception.getMessage());
            event.getPlayer().sendMessage(PREFIX + "§c파티 채팅 전송 중 오류가 발생했습니다.");
        }
    }
}
