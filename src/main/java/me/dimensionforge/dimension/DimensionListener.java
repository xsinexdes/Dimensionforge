package me.dimensionforge.dimension;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.Text;
import net.kyori.adventure.title.Title;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldInitEvent;

/** Permisos de entrada, mensajes/títulos y modo de juego al entrar o salir de una dimensión. */
public final class DimensionListener implements Listener {

    private final DimensionForgePlugin plugin;

    public DimensionListener(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getTo().getWorld() == null || e.getFrom().getWorld() == null) {
            return;
        }
        World to = e.getTo().getWorld();
        if (to.equals(e.getFrom().getWorld())) {
            return;
        }
        DimensionData d = plugin.dimensions().of(to);
        if (d == null || d.permission == null || d.permission.isBlank()) {
            return;
        }
        Player p = e.getPlayer();
        if (!p.hasPermission(d.permission) && !p.hasPermission("dimensionforge.bypass")) {
            e.setCancelled(true);
            plugin.messages().send(p, "dimension.no-permission", "name", d.name);
        }
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        DimensionData from = plugin.dimensions().of(e.getFrom());
        DimensionData to = plugin.dimensions().of(p.getWorld());
        if (from != null) {
            notify(p, from.exitMessage, from.exitTitle);
        }
        if (to != null) {
            notify(p, to.enterMessage, to.enterTitle);
            if (to.defaultGameMode != DimensionData.ModeOpt.NONE && !p.hasPermission("dimensionforge.bypass")) {
                try {
                    p.setGameMode(GameMode.valueOf(to.defaultGameMode.name()));
                } catch (IllegalArgumentException ignored) {
                    // modo inválido, se ignora
                }
            }
        }
    }

    private void notify(Player p, String message, String title) {
        if (message != null && !message.isBlank()) {
            p.sendMessage(Text.mm(message));
        }
        if (title != null && !title.isBlank()) {
            String[] parts = title.split("\\|", 2);
            p.showTitle(Title.title(Text.mm(parts[0]), Text.mm(parts.length > 1 ? parts[1] : "")));
        }
    }

    /** Registra los populators de menas en cada mundo que se inicializa. */
    @EventHandler
    public void onWorldInit(WorldInitEvent e) {
        plugin.onWorldLoaded(e.getWorld());
    }
}
