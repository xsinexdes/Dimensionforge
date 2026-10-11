package me.dimensionforge.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Entrada de texto por chat o por yunque. */
public final class TextInput implements Listener {

    private record Pending(Consumer<String> ok, Runnable cancel, BukkitTask timeout) {
    }

    private static final class AnvilHolder implements InventoryHolder {
        final Consumer<String> ok;
        final Runnable cancel;
        final String initial;
        String text;
        boolean done;
        Inventory inventory;

        AnvilHolder(Consumer<String> ok, Runnable cancel, String initial) {
            this.ok = ok;
            this.cancel = cancel;
            this.initial = initial;
            this.text = initial;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final DimensionForgePlugin plugin;
    private final Map<UUID, Pending> chat = new ConcurrentHashMap<>();

    public TextInput(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    /** Pide un texto al jugador. */
    public void request(Player player, String prompt, String initial, Consumer<String> ok, Runnable cancel) {
        Runnable safeCancel = cancel == null ? () -> {
        } : cancel;
        if ("ANVIL".equals(plugin.configs().inputMode())) {
            openAnvil(player, prompt, initial == null ? "" : initial, ok, safeCancel);
        } else {
            requestChat(player, prompt, ok, safeCancel);
        }
    }

    private void requestChat(Player player, String prompt, Consumer<String> ok, Runnable cancel) {
        player.closeInventory();
        Pending old = chat.remove(player.getUniqueId());
        if (old != null) {
            old.timeout().cancel();
        }
        plugin.messages().send(player, "input.chat-prompt", "prompt", Text.plain(Text.mm(prompt)));
        int ticks = Math.max(5, plugin.configs().inputTimeoutSeconds()) * 20;
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Pending p = chat.remove(player.getUniqueId());
            if (p != null) {
                plugin.messages().send(player, "input.timeout");
                p.cancel().run();
            }
        }, ticks);
        chat.put(player.getUniqueId(), new Pending(ok, cancel, timeout));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Pending p = chat.remove(e.getPlayer().getUniqueId());
        if (p == null) {
            return;
        }
        e.setCancelled(true);
        final String msg = Text.plain(e.message()).trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.timeout().cancel();
            if (msg.equalsIgnoreCase("cancelar") || msg.equalsIgnoreCase("cancel")) {
                plugin.messages().send(e.getPlayer(), "input.cancelled");
                p.cancel().run();
            } else {
                p.ok().accept(msg);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Pending p = chat.remove(e.getPlayer().getUniqueId());
        if (p != null) {
            p.timeout().cancel();
        }
    }

    private void openAnvil(Player player, String prompt, String initial, Consumer<String> ok, Runnable cancel) {
        AnvilHolder holder = new AnvilHolder(ok, cancel, initial);
        Component title = Text.mm(prompt);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.ANVIL, title);
        holder.inventory = inv;
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(Component.text(initial.isEmpty() ? " " : initial));
        paper.setItemMeta(meta);
        inv.setItem(0, paper);
        player.openInventory(inv);
    }

    @EventHandler
    public void onPrepare(PrepareAnvilEvent e) {
        if (!(e.getInventory().getHolder() instanceof AnvilHolder holder)) {
            return;
        }
        String text = null;
        if (e.getView() instanceof AnvilView av) {
            text = av.getRenameText();
        }
        if (text == null || text.isEmpty()) {
            text = holder.initial;
        }
        holder.text = text;
        ItemStack result = new ItemStack(Material.LIME_DYE);
        ItemMeta meta = result.getItemMeta();
        meta.displayName(Component.text(text.isEmpty() ? " " : text));
        result.setItemMeta(meta);
        e.setResult(result);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof AnvilHolder holder)) {
            return;
        }
        e.setCancelled(true);
        if (e.getRawSlot() == 2 && !holder.done) {
            holder.done = true;
            final String text = holder.text == null ? "" : holder.text.trim();
            final Player p = (Player) e.getWhoClicked();
            Bukkit.getScheduler().runTask(plugin, () -> {
                p.closeInventory();
                holder.ok.accept(text);
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AnvilHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof AnvilHolder holder)) {
            return;
        }
        e.getInventory().clear();
        if (!holder.done) {
            holder.done = true;
            Bukkit.getScheduler().runTask(plugin, holder.cancel);
        }
    }
}
