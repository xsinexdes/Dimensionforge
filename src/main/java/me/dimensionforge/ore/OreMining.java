package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.Fx;
import me.dimensionforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Rotura manual de menas: progreso, grietas, herramienta mínima, drops y respawn. */
public final class OreMining {

    private static final class Session {
        final UUID player;
        final Block block;
        final OreDef ore;
        final boolean blocked;
        double progress;
        long lastSwing;
        long startTick;
        int lastStage = -1;
        PotionEffect originalFatigue;
        int originalFatigueLevel;
        boolean fatigueApplied;

        Session(UUID player, Block block, OreDef ore, boolean blocked, long tick) {
            this.player = player;
            this.block = block;
            this.ore = ore;
            this.blocked = blocked;
            this.lastSwing = tick;
            this.startTick = tick;
        }
    }

    private final DimensionForgePlugin plugin;
    private final OreManager ores;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Long> lastSwing = new HashMap<>();
    private final Map<UUID, Long> wrongToolWarn = new HashMap<>();
    private final Set<UUID> internal = new HashSet<>();
    private long tick;

    public OreMining(DimensionForgePlugin plugin, OreManager ores) {
        this.plugin = plugin;
        this.ores = ores;
    }

    public boolean isInternal(Player p) {
        return internal.contains(p.getUniqueId());
    }

    // ---------------------------------------------------------------- herramientas

    private static String tierOf(Material m) {
        String n = m.name();
        if (n.startsWith("WOODEN_")) {
            return "WOOD";
        }
        if (n.startsWith("STONE_")) {
            return "STONE";
        }
        if (n.startsWith("IRON_")) {
            return "IRON";
        }
        if (n.startsWith("GOLDEN_")) {
            return "GOLD";
        }
        if (n.startsWith("DIAMOND_")) {
            return "DIAMOND";
        }
        if (n.startsWith("NETHERITE_")) {
            return "NETHERITE";
        }
        return null;
    }

    private static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_SWORD");
    }

    private int rank(String tier) {
        return plugin.getConfig().getInt("ores.tier-rank." + tier.toUpperCase(Locale.ROOT), defaultRank(tier));
    }

    private static int defaultRank(String tier) {
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "STONE" -> 1;
            case "IRON" -> 2;
            case "DIAMOND" -> 3;
            case "NETHERITE" -> 4;
            default -> 0;
        };
    }

    private double tierSpeed(String tier) {
        double def = switch (tier.toUpperCase(Locale.ROOT)) {
            case "STONE" -> 1.4;
            case "IRON" -> 1.8;
            case "GOLD" -> 2.6;
            case "DIAMOND" -> 2.2;
            case "NETHERITE" -> 2.5;
            default -> 1.0;
        };
        return plugin.getConfig().getDouble("ores.tier-speed." + tier.toUpperCase(Locale.ROOT), def);
    }

    /** ¿Cumple la herramienta los requisitos mínimos de la mena? */
    public boolean toolOk(OreDef o, ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return false;
        }
        if (o.toolMode == OreDef.ToolMode.SPECIFIC) {
            if (!tool.getType().name().equalsIgnoreCase(o.toolMaterial)) {
                return false;
            }
            ItemMeta meta = tool.getItemMeta();
            if (o.toolCmd > 0 && (meta == null || !meta.hasCustomModelData() || meta.getCustomModelData() != o.toolCmd)) {
                return false;
            }
            if (o.toolName != null && !o.toolName.isBlank()) {
                if (meta == null || meta.displayName() == null) {
                    return false;
                }
                String have = Text.plain(meta.displayName());
                String want = Text.plain(Text.mm(o.toolName));
                return have.equals(want);
            }
            return true;
        }
        String tier = tierOf(tool.getType());
        if (tier == null) {
            return false;
        }
        String n = tool.getType().name();
        if (o.pickaxeOnly ? !n.endsWith("_PICKAXE") : !isTool(tool.getType())) {
            return false;
        }
        return rank(tier) >= rank(o.minTier);
    }

    /** Segundos necesarios para romper la mena con la herramienta y estado actuales del jugador. */
    public double seconds(OreDef o, Player p, ItemStack tool, int originalFatigueLevel) {
        double base = Math.max(0.0, o.hardness);
        double mult = 1.0;
        if (o.toolMode == OreDef.ToolMode.TIER && tool != null) {
            String t = tierOf(tool.getType());
            if (t != null) {
                mult *= tierSpeed(t) / Math.max(0.1, tierSpeed(o.minTier));
            }
        }
        if (o.applyModifiers) {
            if (tool != null) {
                int eff = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
                if (eff > 0) {
                    mult *= 1.0 + (eff * eff + 1) * plugin.getConfig().getDouble("ores.efficiency-factor", 0.15);
                }
            }
            PotionEffectType haste = Fx.effect("haste");
            if (haste != null) {
                PotionEffect e = p.getPotionEffect(haste);
                if (e != null) {
                    mult *= 1.0 + plugin.getConfig().getDouble("ores.haste-per-level", 0.2) * (e.getAmplifier() + 1);
                }
            }
            if (originalFatigueLevel > 0) {
                mult *= Math.pow(0.3, Math.min(originalFatigueLevel, 4));
            }
            if (p.isInWater() && !hasAquaAffinity(p)) {
                mult *= plugin.getConfig().getDouble("ores.water-multiplier", 0.2);
            }
            if (!onGround(p)) {
                mult *= plugin.getConfig().getDouble("ores.air-multiplier", 0.2);
            }
        }
        double min = plugin.getConfig().getDouble("ores.min-seconds", 0.05);
        return Math.max(min, base / Math.max(0.001, mult));
    }

    private boolean hasAquaAffinity(Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        return helmet != null && helmet.getEnchantmentLevel(Enchantment.AQUA_AFFINITY) > 0;
    }

    private boolean onGround(Player p) {
        Location l = p.getLocation();
        Block below = l.getWorld().getBlockAt(l.getBlockX(), (int) Math.floor(l.getY() - 0.1), l.getBlockZ());
        return !below.isPassable();
    }

    // ---------------------------------------------------------------- sesiones

    public void onSwing(Player p) {
        lastSwing.put(p.getUniqueId(), tick);
        Session s = sessions.get(p.getUniqueId());
        if (s != null) {
            s.lastSwing = tick;
        }
    }

    /** Se llama desde BlockDamageEvent (ya cancelado el daño vanilla). */
    public void onDamage(Player p, Block b, OreDef o) {
        Session cur = sessions.get(p.getUniqueId());
        if (cur != null && cur.block.equals(b)) {
            cur.lastSwing = tick;
            return;
        }
        if (cur != null) {
            end(p.getUniqueId());
        }
        ItemStack tool = p.getInventory().getItemInMainHand();
        boolean ok = toolOk(o, tool);
        boolean blocked = !ok && !o.wrongToolBreaks;
        if (!ok) {
            long now = System.currentTimeMillis();
            Long last = wrongToolWarn.get(p.getUniqueId());
            if (last == null || now - last > 1500) {
                wrongToolWarn.put(p.getUniqueId(), now);
                plugin.messages().send(p, "ore.wrong-tool", "tool", describeTool(o));
                Fx.sound(p, o.wrongToolSound, 1f, 0.8f);
            }
        }
        Session s = new Session(p.getUniqueId(), b, o, blocked, tick);
        s.progress = 0;
        PotionEffectType fatigue = Fx.effect("mining_fatigue");
        if (fatigue != null) {
            PotionEffect existing = p.getPotionEffect(fatigue);
            if (existing != null && existing.getAmplifier() < 100) {
                s.originalFatigue = existing;
                s.originalFatigueLevel = existing.getAmplifier() + 1;
            }
        }
        sessions.put(p.getUniqueId(), s);
        applyFatigue(p, s);
    }

    public void onAbort(Player p, Block b) {
        Session s = sessions.get(p.getUniqueId());
        if (s != null && s.block.equals(b)) {
            end(p.getUniqueId());
        }
    }

    private String describeTool(OreDef o) {
        if (o.toolMode == OreDef.ToolMode.SPECIFIC) {
            return o.toolName == null || o.toolName.isBlank() ? o.toolMaterial : Text.plain(Text.mm(o.toolName));
        }
        return (o.pickaxeOnly ? "pico de " : "herramienta de ") + o.minTier.toLowerCase(Locale.ROOT) + " o mejor";
    }

    private void applyFatigue(Player p, Session s) {
        PotionEffectType fatigue = Fx.effect("mining_fatigue");
        if (fatigue == null) {
            return;
        }
        p.addPotionEffect(new PotionEffect(fatigue, 30, 255, false, false, false));
        s.fatigueApplied = true;
    }

    /** Termina la sesión de un jugador, limpia grietas y restaura su fatiga original. */
    public void end(UUID id) {
        Session s = sessions.remove(id);
        if (s == null) {
            return;
        }
        Player p = Bukkit.getPlayer(id);
        if (p != null) {
            finish(p, s);
        }
    }

    /** Limpieza de una sesión ya retirada del mapa. */
    private void finish(Player p, Session s) {
        p.sendBlockDamage(s.block.getLocation(), 0f);
        if (s.fatigueApplied) {
            PotionEffectType fatigue = Fx.effect("mining_fatigue");
            if (fatigue != null) {
                p.removePotionEffect(fatigue);
                if (s.originalFatigue != null) {
                    int elapsed = (int) Math.max(0, tick - s.startTick);
                    int remaining = s.originalFatigue.getDuration() - elapsed;
                    if (remaining > 0 || s.originalFatigue.getDuration() < 0) {
                        p.addPotionEffect(new PotionEffect(fatigue, remaining > 0 ? remaining : -1, s.originalFatigue.getAmplifier(),
                                s.originalFatigue.isAmbient(), s.originalFatigue.hasParticles(), s.originalFatigue.hasIcon()));
                    }
                }
            }
        }
    }

    public void endAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            end(id);
        }
    }

    public void forget(UUID id) {
        end(id);
        lastSwing.remove(id);
        wrongToolWarn.remove(id);
    }

    /** Tarea central, una vez por tick, para todas las sesiones. */
    public void tick() {
        tick++;
        if (sessions.isEmpty()) {
            return;
        }
        int swingTimeout = plugin.getConfig().getInt("ores.swing-timeout-ticks", 8);
        double decay = plugin.getConfig().getDouble("ores.progress-decay-per-tick", 0.05);
        for (Iterator<Map.Entry<UUID, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Session> en = it.next();
            Session s = en.getValue();
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline() || p.isDead() || !p.getWorld().equals(s.block.getWorld())
                    || p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
                it.remove();
                if (p != null) {
                    finish(p, s);
                }
                continue;
            }
            if (ores.activeAt(s.block) != s.ore) {
                it.remove();
                finish(p, s);
                continue;
            }
            boolean swinging = tick - s.lastSwing <= swingTimeout;
            Block target = p.getTargetBlockExact(6);
            boolean looking = target != null && target.equals(s.block);
            if (tick % 10 == 0 && s.fatigueApplied) {
                applyFatigue(p, s);
            }
            if (s.blocked) {
                if (!swinging && !looking) {
                    it.remove();
                    finish(p, s);
                }
                continue;
            }
            ItemStack tool = p.getInventory().getItemInMainHand();
            if (!toolOk(s.ore, tool) && !s.ore.wrongToolBreaks) {
                it.remove();
                finish(p, s);
                continue;
            }
            if (looking && swinging) {
                double seconds = seconds(s.ore, p, tool, s.originalFatigueLevel);
                s.progress += 1.0 / (seconds * 20.0);
                if ((tick - s.startTick) % 4 == 0) {
                    String hit = s.ore.hitSound == null || s.ore.hitSound.isBlank() ? "block.stone.hit" : s.ore.hitSound;
                    Fx.sound(s.block.getLocation().add(0.5, 0.5, 0.5), hit, 0.6f, 0.9f);
                }
            } else {
                s.progress -= decay;
            }
            if (s.progress >= 1.0) {
                it.remove();
                finish(p, s);
                complete(p, s.block, s.ore);
                continue;
            }
            if (s.progress <= 0.0) {
                it.remove();
                finish(p, s);
                continue;
            }
            int stage = (int) Math.floor(s.progress * 10);
            if (stage != s.lastStage || tick % 20 == 0) {
                s.lastStage = stage;
                p.sendBlockDamage(s.block.getLocation(), (float) s.progress);
            }
        }
    }

    // ---------------------------------------------------------------- rotura

    /** Rompe la mena: respeta protecciones, entrega drops, desgasta la herramienta y programa el respawn. */
    public void complete(Player p, Block b, OreDef o) {
        ItemStack tool = p.getInventory().getItemInMainHand();
        boolean toolIsOk = toolOk(o, tool);
        BlockBreakEvent ev = new BlockBreakEvent(b, p);
        ev.setDropItems(false);
        ev.setExpToDrop(0);
        internal.add(p.getUniqueId());
        try {
            Bukkit.getPluginManager().callEvent(ev);
        } finally {
            internal.remove(p.getUniqueId());
        }
        if (ev.isCancelled()) {
            return;
        }
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        World w = b.getWorld();

        // drops
        List<ItemStack> drops = new ArrayList<>();
        boolean canDrop = toolIsOk || o.wrongToolDrops;
        if (canDrop) {
            if (o.silkTouchDropsBlock && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
                drops.add(new ItemStack(o.base()));
            } else {
                drops.addAll(rollDrops(o, tool));
            }
        }
        if (o.dropsToInventory) {
            for (ItemStack it : drops) {
                for (ItemStack left : p.getInventory().addItem(it).values()) {
                    w.dropItemNaturally(center, left);
                }
            }
        } else {
            for (ItemStack it : drops) {
                w.dropItemNaturally(center, it);
            }
        }
        // experiencia
        if (canDrop && o.xpMax > 0) {
            int lo = Math.max(0, Math.min(o.xpMin, o.xpMax));
            int xp = lo + ThreadLocalRandom.current().nextInt(Math.max(o.xpMax, lo) - lo + 1);
            if (xp > 0) {
                if (o.dropsToInventory) {
                    p.giveExp(xp);
                } else {
                    w.spawn(center, ExperienceOrb.class, orb -> orb.setExperience(xp));
                }
            }
        }
        // efectos
        Particle part = Fx.particle(o.breakParticle, Particle.BLOCK);
        if (part == Particle.BLOCK) {
            w.spawnParticle(Particle.BLOCK, center, Math.max(1, o.breakParticleCount), 0.3, 0.3, 0.3, 0.1, b.getBlockData());
        } else {
            w.spawnParticle(part, center, Math.max(1, o.breakParticleCount), 0.3, 0.3, 0.3, 0.05);
        }
        Fx.sound(center, o.breakSound, 1f, 1f);

        // durabilidad
        if (!o.noDurability && o.durabilityCost > 0) {
            damageTool(p, tool, o.durabilityCost);
        }

        // reemplazo del bloque
        if (o.respawnSeconds > 0) {
            ores.deplete(b, o);
        } else {
            ores.unregister(b);
            b.setType(Material.AIR, false);
        }
    }

    private List<ItemStack> rollDrops(OreDef o, ItemStack tool) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int fortune = o.fortune && tool != null ? tool.getEnchantmentLevel(Enchantment.FORTUNE) : 0;
        boolean linear = "LINEAR".equalsIgnoreCase(plugin.getConfig().getString("ores.fortune-formula", "VANILLA"));
        List<ItemStack> out = new ArrayList<>();
        for (DropEntry d : o.drops) {
            if (!d.roll(rnd)) {
                continue;
            }
            int amount = d.rollAmount(rnd);
            if (fortune > 0 && amount > 0) {
                if (linear) {
                    amount += rnd.nextInt(fortune + 1);
                } else {
                    int bonus = Math.max(0, rnd.nextInt(fortune + 2) - 1);
                    amount *= bonus + 1;
                }
            }
            while (amount > 0) {
                int stack = Math.min(amount, d.mat().getMaxStackSize());
                out.add(d.create(stack));
                amount -= stack;
            }
        }
        return out;
    }

    private void damageTool(Player p, ItemStack tool, int amount) {
        if (tool == null || tool.getType().isAir()) {
            return;
        }
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable dm) || meta.isUnbreakable() || tool.getType().getMaxDurability() <= 0) {
            return;
        }
        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int real = 0;
        for (int i = 0; i < amount; i++) {
            if (unbreaking <= 0 || rnd.nextInt(unbreaking + 1) == 0) {
                real++;
            }
        }
        if (real == 0) {
            return;
        }
        dm.setDamage(dm.getDamage() + real);
        tool.setItemMeta(dm);
        if (dm.getDamage() >= tool.getType().getMaxDurability()) {
            p.getInventory().setItemInMainHand(null);
            Fx.sound(p, "entity.item.break", 1f, 1f);
        }
    }
}
