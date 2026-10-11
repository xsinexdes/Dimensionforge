package me.dimensionforge.skill;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.dimension.DimensionData;
import me.dimensionforge.mob.MobState;
import me.dimensionforge.skill.model.BossPhase;
import me.dimensionforge.skill.model.SkillBinding;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.skill.model.Trigger;
import me.dimensionforge.util.Fx;
import me.dimensionforge.util.Keys;
import me.dimensionforge.util.YamlIO;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Planificador central de habilidades: un único tick de servidor evalúa disparadores, ejecuta acciones
 * diferidas, caduca bloques e invocaciones temporales y aplica presupuestos para no saturar el servidor.
 */
public final class SkillEngine {

    private record Timed(UUID owner, Runnable action) {
    }

    public record BlockKey(String world, int x, int y, int z) {
    }

    private record TempBlock(BlockData original, long expiresTick, UUID owner) {
    }

    private record Summon(UUID owner, long expiresTick, String model, UUID entity) {
    }

    private record Reflect(long until, double fraction) {
    }

    private final DimensionForgePlugin plugin;
    private final SkillManager manager;
    private final SkillExecutor executor;
    private final File tempFile;

    private volatile long tick;
    private BukkitTask task;
    private final TreeMap<Long, List<Timed>> timeline = new TreeMap<>();
    private final Map<UUID, Map<String, Long>> readyAt = new HashMap<>();

    // presupuestos por tick
    private final int particlesPerTick;
    private final int actionsPerTick;
    private final int maxTempBlocks;
    private final int maxSummonsPerMob;
    private final int triggerPeriod;
    private int particlesLeft;
    private int actionsLeft;

    // bloques temporales
    private final Map<BlockKey, TempBlock> tempBlocks = new HashMap<>();
    private boolean tempDirty;

    // invocaciones
    private final Map<UUID, Summon> summons = new HashMap<>();

    // estados sobre jugadores / mobs
    private final Map<UUID, Long> stunned = new HashMap<>();
    private final Map<UUID, Long> silenced = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, Long> marked = new HashMap<>();
    private final Map<UUID, Reflect> reflecting = new HashMap<>();
    /** Jugadores que están probando una habilidad desde el menú (se permiten aunque estén en creativo). */
    private final Map<UUID, Long> testers = new HashMap<>();

    private boolean casting;

    public SkillEngine(DimensionForgePlugin plugin, SkillManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.tempFile = new File(new File(plugin.getDataFolder(), "skills"), "temp_blocks.yml");
        this.particlesPerTick = plugin.getConfig().getInt("skills.max-particles-per-tick", 600);
        this.actionsPerTick = plugin.getConfig().getInt("skills.max-actions-per-tick", 300);
        this.maxTempBlocks = plugin.getConfig().getInt("skills.max-temp-blocks", 400);
        this.maxSummonsPerMob = plugin.getConfig().getInt("skills.max-summons-per-mob", 12);
        this.triggerPeriod = Math.max(1, plugin.getConfig().getInt("skills.trigger-period-ticks", 5));
        this.executor = new SkillExecutor(plugin, manager, this);
    }

    // ---------------------------------------------------------------- ciclo de vida

    public void start() {
        restoreLeftoverBlocks();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        // Todo lo temporal desaparece al apagar: bloques restaurados e invocaciones eliminadas.
        for (Map.Entry<BlockKey, TempBlock> e : new ArrayList<>(tempBlocks.entrySet())) {
            restore(e.getKey(), e.getValue());
        }
        tempBlocks.clear();
        for (Summon s : new ArrayList<>(summons.values())) {
            removeSummon(s, false);
        }
        summons.clear();
        timeline.clear();
        saveTempBlocksNow();
    }

    public long now() {
        return tick;
    }

    public boolean casting() {
        return casting;
    }

    void setCasting(boolean v) {
        casting = v;
    }

    public SkillExecutor executor() {
        return executor;
    }

    public DimensionForgePlugin plugin() {
        return plugin;
    }

    private void tick() {
        tick++;
        particlesLeft = particlesPerTick;
        actionsLeft = actionsPerTick;
        runTimeline();
        if (tick % triggerPeriod == 0) {
            evaluateTriggers();
        }
        if (tick % 10 == 0) {
            expireTempBlocks();
            expireSummons();
        }
        if (tick % 40 == 0) {
            cleanStatus();
        }
        if (tempDirty && tick % 100 == 0) {
            saveTempBlocksAsync();
        }
    }

    // ---------------------------------------------------------------- acciones diferidas

    /** Programa una acción para dentro de delayTicks. */
    public void later(UUID owner, long delayTicks, Runnable action) {
        long at = tick + Math.max(1, delayTicks);
        timeline.computeIfAbsent(at, k -> new ArrayList<>()).add(new Timed(owner, action));
    }

    private void runTimeline() {
        while (!timeline.isEmpty() && timeline.firstKey() <= tick) {
            Map.Entry<Long, List<Timed>> e = timeline.pollFirstEntry();
            List<Timed> list = e.getValue();
            for (int i = 0; i < list.size(); i++) {
                if (actionsLeft <= 0) {
                    // Presupuesto agotado: lo que queda pasa al siguiente tick.
                    List<Timed> rest = new ArrayList<>(list.subList(i, list.size()));
                    timeline.computeIfAbsent(tick + 1, k -> new ArrayList<>()).addAll(0, rest);
                    return;
                }
                actionsLeft--;
                boolean prev = casting;
                casting = true;
                try {
                    list.get(i).action().run();
                } catch (Exception ex) {
                    plugin.getLogger().warning("Error en acción de habilidad: " + ex);
                } finally {
                    casting = prev;
                }
            }
        }
    }

    /** Cancela las acciones pendientes de un dueño (sin tocar las de ON_DEATH ya programadas tras morir). */
    public void cancelActions(UUID owner) {
        for (List<Timed> l : timeline.values()) {
            l.removeIf(t -> t.owner().equals(owner));
        }
    }

    // ---------------------------------------------------------------- presupuesto de partículas

    /** Devuelve cuántas partículas se pueden gastar de las pedidas este tick. */
    public int particles(int wanted) {
        int n = Math.min(wanted, particlesLeft);
        particlesLeft -= Math.max(0, n);
        return Math.max(0, n);
    }

    public void particle(Location loc, Particle p, int count, double spread, Color color) {
        int n = particles(count);
        if (n > 0 && p != null) {
            Fx.spawn(loc, p, n, spread, color);
        }
    }

    // ---------------------------------------------------------------- disparadores

    private boolean skillsAllowed(MobState st) {
        if (!st.def.skillsEnabled || !st.entity.isValid() || st.entity.isDead()) {
            return false;
        }
        DimensionData dd = plugin.dimensions().of(st.entity.getWorld());
        return dd == null || dd.skillsEnabled;
    }

    /** Jugador válido como objetivo: vivo, ni creativo ni espectador y sin protección de WorldGuard. */
    public boolean validPlayer(Player p) {
        if (p == null || !p.isOnline() || p.isDead()) {
            return false;
        }
        if (testers.getOrDefault(p.getUniqueId(), 0L) > tick) {
            return true;
        }
        GameMode gm = p.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) {
            return false;
        }
        return manager.worldGuard() == null || manager.worldGuard().allowMobDamage(p.getLocation());
    }

    public Player nearestPlayer(Location from, double range) {
        Player best = null;
        double bestD = range * range;
        World w = from.getWorld();
        if (w == null) {
            return null;
        }
        for (Player p : w.getPlayers()) {
            if (!validPlayer(p)) {
                continue;
            }
            double d = p.getLocation().distanceSquared(from);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private void evaluateTriggers() {
        for (MobState st : new ArrayList<>(plugin.mobs().active())) {
            try {
                evaluate(st);
            } catch (Exception ex) {
                plugin.getLogger().warning("Error evaluando habilidades de " + st.def.id + ": " + ex);
            }
        }
    }

    private void evaluate(MobState st) {
        if (!skillsAllowed(st)) {
            return;
        }
        List<SkillBinding> bindings = plugin.mobs().activeBindings(st);
        if (bindings.isEmpty()) {
            return;
        }
        LivingEntity e = st.entity;
        Location loc = e.getLocation();
        Player nearest = nearestPlayer(loc, 64);
        if (nearest == null) {
            return;
        }
        double dist = nearest.getLocation().distance(loc);
        double engage = Math.min(Math.max(st.def.followRange, 16), 48);
        double maxHp = e.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null ? 1
                : e.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
        double pct = e.getHealth() / Math.max(1.0, maxHp) * 100.0;
        for (SkillBinding b : bindings) {
            if (!b.enabled) {
                continue;
            }
            SkillDef base = manager.get(b.skillId);
            if (base == null) {
                continue;
            }
            SkillDef eff = b.applyTo(base);
            boolean go = switch (eff.trigger) {
                case INTERVAL -> dist <= engage;
                case SEE_PLAYER -> dist <= engage && e.hasLineOfSight(nearest);
                case PLAYER_NEAR -> dist <= eff.triggerValue;
                case PLAYER_FAR -> dist >= eff.triggerValue && dist <= 64;
                case IDLE -> dist <= engage && (plugin.mobs().tickCount() - st.idleSinceTick) >= eff.triggerValue * 20;
                case HEALTH_BELOW -> pct <= eff.triggerValue && dist <= engage;
                case HEALTH_ABOVE -> pct >= eff.triggerValue && dist <= engage;
                default -> false;
            };
            if (go) {
                executor.cast(st, b, eff, nearest);
            }
        }
    }

    /** Dispara los disparadores basados en eventos (ON_ATTACK, ON_DAMAGED, ON_DEATH, ON_SPAWN). */
    public void fire(MobState st, Trigger trigger, LivingEntity other) {
        if (casting || !skillsAllowed(st) && trigger != Trigger.ON_DEATH) {
            return;
        }
        if (!st.def.skillsEnabled) {
            return;
        }
        for (SkillBinding b : plugin.mobs().activeBindings(st)) {
            if (!b.enabled) {
                continue;
            }
            SkillDef base = manager.get(b.skillId);
            if (base == null) {
                continue;
            }
            SkillDef eff = b.applyTo(base);
            if (eff.trigger == trigger) {
                executor.cast(st, b, eff, other);
            }
        }
    }

    public void onHitByWeapon(MobState st, Player attacker, ItemStack weapon, boolean crit) {
        if (casting || !skillsAllowed(st)) {
            return;
        }
        for (SkillBinding b : plugin.mobs().activeBindings(st)) {
            if (!b.enabled) {
                continue;
            }
            SkillDef base = manager.get(b.skillId);
            if (base == null) {
                continue;
            }
            SkillDef eff = b.applyTo(base);
            if (eff.trigger == Trigger.ON_CRIT && crit) {
                executor.cast(st, b, eff, attacker);
            } else if (eff.trigger == Trigger.HIT_BY_WEAPON && weaponMatches(weapon, eff.weapon)) {
                executor.cast(st, b, eff, attacker);
            }
        }
    }

    static boolean weaponMatches(ItemStack item, String filter) {
        if (filter == null || filter.isBlank() || filter.equalsIgnoreCase("ANY")) {
            return true;
        }
        if (item == null) {
            return false;
        }
        String n = item.getType().name();
        String f = filter.toUpperCase();
        return n.equals(f) || n.endsWith("_" + f);
    }

    /** Al entrar en una fase se lanzan las habilidades de esa fase con disparador ON_SPAWN ("al empezar la fase"). */
    public void onPhaseEntered(MobState st, BossPhase phase) {
        if (!skillsAllowed(st)) {
            return;
        }
        for (SkillBinding b : phase.skills) {
            if (!b.enabled) {
                continue;
            }
            SkillDef base = manager.get(b.skillId);
            if (base == null) {
                continue;
            }
            SkillDef eff = b.applyTo(base);
            if (eff.trigger == Trigger.ON_SPAWN) {
                executor.cast(st, b, eff, null);
            }
        }
    }

    public void forgetMob(MobState st) {
        readyAt.remove(st.id);
    }

    // ---------------------------------------------------------------- enfriamientos

    public boolean ready(MobState st, String key) {
        Map<String, Long> m = readyAt.get(st.id);
        return m == null || m.getOrDefault(key, 0L) <= tick;
    }

    public void setCooldown(MobState st, String key, double seconds) {
        readyAt.computeIfAbsent(st.id, k -> new HashMap<>()).put(key, tick + (long) Math.ceil(seconds * 20.0));
    }

    /** Pone todos los enfriamientos a cero (botón "reiniciar" del menú de pruebas). */
    public void resetCooldowns(MobState st) {
        readyAt.remove(st.id);
    }

    // ---------------------------------------------------------------- bloques temporales

    public boolean placeTemp(Block b, BlockData data, long durationTicks, UUID owner) {
        if (tempBlocks.size() >= maxTempBlocks || !b.getType().isAir()) {
            return false;
        }
        if (manager.worldGuard() != null && !manager.worldGuard().allowBlockChange(b.getLocation())) {
            return false;
        }
        BlockKey k = new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        if (tempBlocks.containsKey(k)) {
            return false;
        }
        BlockData original = b.getBlockData();
        b.setBlockData(data, false);
        tempBlocks.put(k, new TempBlock(original, tick + durationTicks, owner));
        tempDirty = true;
        return true;
    }

    public boolean isTempBlock(Block b) {
        return tempBlocks.containsKey(new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()));
    }

    /** Un jugador rompió un bloque temporal: se olvida sin soltar nada. */
    public void forgetTemp(Block b) {
        if (tempBlocks.remove(new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ())) != null) {
            tempDirty = true;
        }
    }

    private void restore(BlockKey k, TempBlock t) {
        World w = Bukkit.getWorld(k.world());
        if (w == null) {
            return;
        }
        Block b = w.getBlockAt(k.x(), k.y(), k.z());
        // Solo se restaura si sigue siendo el bloque temporal (nadie lo cambió por encima).
        if (!b.getType().isAir() && b.getType() != t.original().getMaterial()) {
            b.setBlockData(t.original(), false);
        }
    }

    private void expireTempBlocks() {
        boolean any = false;
        for (Iterator<Map.Entry<BlockKey, TempBlock>> it = tempBlocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockKey, TempBlock> e = it.next();
            if (e.getValue().expiresTick() <= tick) {
                restore(e.getKey(), e.getValue());
                it.remove();
                any = true;
            }
        }
        if (any) {
            tempDirty = true;
        }
    }

    private void saveTempBlocksAsync() {
        tempDirty = false;
        YamlIO.saveAsync(plugin, tempFile, tempYaml());
    }

    private void saveTempBlocksNow() {
        YamlIO.saveSync(plugin, tempFile, tempYaml());
    }

    private YamlConfiguration tempYaml() {
        YamlConfiguration y = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        for (Map.Entry<BlockKey, TempBlock> e : tempBlocks.entrySet()) {
            BlockKey k = e.getKey();
            lines.add(k.world() + ";" + k.x() + ";" + k.y() + ";" + k.z() + ";" + e.getValue().original().getAsString());
        }
        y.set("blocks", lines);
        return y;
    }

    /** Tras un cierre brusco pueden quedar bloques temporales: se restauran al arrancar. */
    private void restoreLeftoverBlocks() {
        if (!tempFile.exists()) {
            return;
        }
        YamlConfiguration y = YamlIO.load(plugin, tempFile);
        int restored = 0;
        for (String line : y.getStringList("blocks")) {
            String[] p = line.split(";", 5);
            if (p.length != 5) {
                continue;
            }
            World w = Bukkit.getWorld(p[0]);
            if (w == null) {
                continue;
            }
            try {
                Block b = w.getBlockAt(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
                b.setBlockData(Bukkit.createBlockData(p[4]), false);
                restored++;
            } catch (IllegalArgumentException ignored) {
                // línea corrupta
            }
        }
        if (restored > 0) {
            plugin.getLogger().info("Restaurados " + restored + " bloques temporales de habilidades que quedaron tras un cierre brusco.");
        }
        YamlIO.saveSync(plugin, tempFile, new YamlConfiguration());
    }

    // ---------------------------------------------------------------- invocaciones

    public int summonCount(UUID owner) {
        int n = 0;
        for (Summon s : summons.values()) {
            if (s.owner().equals(owner)) {
                n++;
            }
        }
        return n;
    }

    public boolean canSummon(UUID owner, int extra) {
        return summonCount(owner) + extra <= maxSummonsPerMob;
    }

    public void registerSummon(LivingEntity e, UUID owner, long lifeTicks, String model) {
        e.getPersistentDataContainer().set(Keys.SKILL_SUMMON, PersistentDataType.STRING, owner.toString());
        e.setPersistent(false);
        e.setRemoveWhenFarAway(true);
        summons.put(e.getUniqueId(), new Summon(owner, tick + lifeTicks, model, e.getUniqueId()));
    }

    public boolean isSummon(Entity e) {
        return e.getPersistentDataContainer().has(Keys.SKILL_SUMMON, PersistentDataType.STRING);
    }

    public void forgetSummon(Entity e) {
        Summon s = summons.remove(e.getUniqueId());
        if (s != null && !s.model().isEmpty()) {
            plugin.models().remove(e, s.model());
        }
    }

    private void removeSummon(Summon s, boolean poof) {
        Entity e = Bukkit.getEntity(s.entity());
        if (e != null) {
            if (!s.model().isEmpty()) {
                plugin.models().remove(e, s.model());
            }
            if (poof && e.isValid()) {
                particle(e.getLocation().add(0, 0.5, 0), Fx.particle("POOF", Particle.CRIT), 8, 0.3, null);
            }
            e.remove();
        }
    }

    public void removeSummonsOf(UUID owner) {
        for (Iterator<Summon> it = summons.values().iterator(); it.hasNext(); ) {
            Summon s = it.next();
            if (s.owner().equals(owner)) {
                removeSummon(s, true);
                it.remove();
            }
        }
    }

    private void expireSummons() {
        for (Iterator<Summon> it = summons.values().iterator(); it.hasNext(); ) {
            Summon s = it.next();
            Entity e = Bukkit.getEntity(s.entity());
            if (e == null || !e.isValid()) {
                // Puede estar en un chunk descargado: al no ser persistente ya no existe.
                if (e == null) {
                    it.remove();
                }
                continue;
            }
            if (s.expiresTick() <= tick) {
                removeSummon(s, true);
                it.remove();
            }
        }
    }

    /** Al cargar un chunk se eliminan invocaciones que hubieran quedado guardadas (no deberían, pero por seguridad). */
    public void sweepLoaded(Entity e) {
        if (isSummon(e) && !summons.containsKey(e.getUniqueId())) {
            e.remove();
        }
    }

    // ---------------------------------------------------------------- estados

    public void addTester(UUID id, long ticks) {
        testers.put(id, tick + ticks);
    }

    public void stun(UUID id, long ticks) {
        stunned.merge(id, tick + ticks, Math::max);
    }

    public boolean isStunned(UUID id) {
        return stunned.getOrDefault(id, 0L) > tick;
    }

    public void silence(UUID id, long ticks) {
        silenced.merge(id, tick + ticks, Math::max);
    }

    public boolean isSilenced(UUID id) {
        return silenced.getOrDefault(id, 0L) > tick;
    }

    public void mark(UUID id, long ticks) {
        marked.merge(id, tick + ticks, Math::max);
    }

    /** Multiplicador de daño que sufre una víctima marcada por mobs custom. */
    public double damageMultiplierFor(Entity victim) {
        return marked.getOrDefault(victim.getUniqueId(), 0L) > tick ? 1.3 : 1.0;
    }

    public void reflect(UUID mob, double fraction, long ticks) {
        reflecting.put(mob, new Reflect(tick + ticks, fraction));
    }

    public double reflectFraction(UUID mob) {
        Reflect r = reflecting.get(mob);
        return r != null && r.until() > tick ? r.fraction() : 0;
    }

    private void cleanStatus() {
        stunned.values().removeIf(t -> t <= tick);
        silenced.values().removeIf(t -> t <= tick);
        marked.values().removeIf(t -> t <= tick);
        reflecting.values().removeIf(r -> r.until() <= tick);
        testers.values().removeIf(t -> t <= tick);
    }

    public static ThreadLocalRandom rnd() {
        return ThreadLocalRandom.current();
    }
}
