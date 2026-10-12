package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/** Eventos de menas custom: rotura manual, protecciones y carga de chunks. */
public final class OreListener implements Listener {

    private final DimensionForgePlugin plugin;
    private final OreManager ores;
    private final OreMining mining;

    public OreListener(DimensionForgePlugin plugin, OreManager ores, OreMining mining) {
        this.plugin = plugin;
        this.ores = ores;
        this.mining = mining;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(BlockDamageEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        Block b = e.getBlock();
        OreDef o = ores.activeAt(b);
        if (o != null) {
            e.setCancelled(true);
            mining.onDamage(p, b, o);
        } else if (ores.isDepleted(b)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onAbort(BlockDamageAbortEvent e) {
        mining.onAbort(e.getPlayer(), e.getBlock());
    }

    @EventHandler
    public void onAnimation(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) {
            mining.onSwing(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (mining.isInternal(p)) {
            return;
        }
        Block b = e.getBlock();
        OreDef o = ores.oreAt(b);
        if (o == null) {
            return;
        }
        boolean creative = p.getGameMode() == GameMode.CREATIVE && p.hasPermission("dimensionforge.admin");
        if (creative) {
            // un admin en creativo puede quitar la mena sin drops
            e.setDropItems(false);
            e.setExpToDrop(0);
            ores.unregister(b);
            return;
        }
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(ores::isOrePosition);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(ores::isOrePosition);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) {
            if (ores.isOrePosition(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) {
            if (ores.isOrePosition(b)) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (ores.isOrePosition(e.getBlock())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        mining.forget(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        ores.onChunkLoad(e.getChunk());
        ores.lazyGenerate(e.getChunk(), e.isNewChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent e) {
        ores.onChunkUnload(e.getChunk());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent e) {
        ores.onWorldUnload(e.getWorld());
    }
}
