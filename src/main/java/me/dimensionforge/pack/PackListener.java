package me.dimensionforge.pack;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Envío automático del pack y seguimiento del estado de descarga. */
public final class PackListener implements Listener {

    private final DimensionForgePlugin plugin;
    private final Map<UUID, PlayerResourcePackStatusEvent.Status> status = new HashMap<>();

    public PackListener(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    public PlayerResourcePackStatusEvent.Status statusOf(UUID id) {
        return status.get(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!plugin.getConfig().getBoolean("pack.send-on-join", true)) {
            return;
        }
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.packs().send(p);
            }
        }, 40L);
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) {
        if (!plugin.getConfig().getBoolean("pack.send-on-dimension", true)) {
            return;
        }
        Player p = e.getPlayer();
        if (plugin.dimensions().isManaged(p.getWorld()) && status.get(p.getUniqueId()) == null) {
            plugin.packs().send(p);
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        status.put(e.getPlayer().getUniqueId(), e.getStatus());
        switch (e.getStatus()) {
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED ->
                    plugin.messages().send(e.getPlayer(), "pack.status-failed", "status", e.getStatus().name());
            case DECLINED -> plugin.messages().send(e.getPlayer(), "pack.status-declined");
            default -> {
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        status.remove(e.getPlayer().getUniqueId());
    }
}
