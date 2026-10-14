package com.mikaeel.nickbook.listener;

import com.mikaeel.nickbook.NickBookPlugin;
import com.mikaeel.nickbook.service.NickService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Chat formatting now lives in the chat module (chat.yml). */
public final class NickListener implements Listener {
    private final NickService service;

    public NickListener(NickBookPlugin plugin, NickService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent e) {
        if (service.isNicked(e.getPlayer())) service.applySilently(e.getPlayer());
    }
}
