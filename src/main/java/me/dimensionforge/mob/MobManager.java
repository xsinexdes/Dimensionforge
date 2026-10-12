package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.ore.DropEntry;
import me.dimensionforge.skill.model.BossPhase;
import me.dimensionforge.skill.model.SkillBinding;
import me.dimensionforge.skill.model.Trigger;
import me.dimensionforge.util.Fx;
import me.dimensionforge.util.Keys;
import me.dimensionforge.util.Text;
import me.dimensionforge.util.YamlIO;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Registro y ciclo de vida de los mobs custom. */
public final class MobManager {

    /** Intervalo del tick interno de mobs, en ticks de servidor. */
    private static final int PERIOD = 5;
    private static final String TEST_ID = "__model_test__";

    private final DimensionForgePlugin plugin;
    private final File file;
    private final Map<String, MobDef> defs = new LinkedHashMap<>();
    private final Map<UUID, MobState> active = new HashMap<>();
    private final List<LivingEntity> testEntities = new ArrayList<>();
    private BukkitTask task;
    private long tickCounter;

    public MobManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mobs.yml");
    }

    // ---------------------------------------------------------------- persistencia

    public void load() {
        defs.clear();
        YamlConfiguration yaml = YamlIO.load(plugin, file);
        ConfigurationSection root = yaml.getConfigurationSection("mobs");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) {
                    defs.put(id, MobDef.load(id, s));
                }
            }
        }
        // Reconstruye el estado de los mobs ya cargados (recarga del plugin).
        for (MobState st : new ArrayList<>(active.values())) {
            releaseState(st, false);
        }
        active.clear();
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                adopt(e);
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (MobDef d : defs.values()) {
            d.save(yaml.createSection("mobs." + d.id));
        }
        YamlIO.saveAsync(plugin, file, yaml);
    }

    /** Llamar tras editar una definición: guarda y refresca los mobs vivos de ese tipo. */
    public void changed(MobDef def) {
        save();
        refresh(def);
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
    }

    private void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void shutdown() {
        stop();
        for (LivingEntity e : new ArrayList<>(testEntities)) {
            removeTest(e);
        }
        for (MobState st : new ArrayList<>(active.values())) {
            releaseState(st, false);
        }
        active.clear();
    }

    // ---------------------------------------------------------------- registro

    public MobDef get(String id) {
        return defs.get(id);
    }

    public Collection<MobDef> all() {
        return defs.values();
    }

    public List<MobDef> forDimension(String dimension) {
        List<MobDef> out = new ArrayList<>();
        for (MobDef d : defs.values()) {
            if (d.dimension.equals(dimension)) {
                out.add(d);
            }
        }
        return out;
    }

    public MobDef create(String dimension, String id, EntityType type) {
        MobDef d = new MobDef(id);
        d.dimension = dimension;
        d.entityType = type.name();
        defs.put(id, d);
        save();
        return d;
    }

    /** Borra la definición y todos los mobs vivos de ese tipo (sin dejar modelos huérfanos). */
    public void remove(MobDef def) {
        defs.remove(def.id);
        for (MobState st : new ArrayList<>(active.values())) {
            if (st.def == def) {
                discard(st);
            }
        }
        // Mobs en chunks descargados: se limpian al cargar (adopt) porque ya no hay definición.
        save();
    }

    public void removeByDimension(String dimension) {
        for (MobDef d : new ArrayList<>(forDimension(dimension))) {
            remove(d);
        }
    }

    public Collection<MobState> active() {
        return active.values();
    }

    public MobState state(Entity e) {
        return e == null ? null : active.get(e.getUniqueId());
    }

    public long tickCount() {
        return tickCounter;
    }

    // ---------------------------------------------------------------- aparición

    public LivingEntity spawn(MobDef def, Location loc) {
        World w = loc.getWorld();
        Class<? extends LivingEntity> cls = classOf(def.type());
        if (w == null || cls == null) {
            return null;
        }
        Consumer<LivingEntity> configure = e -> {
            e.getPersistentDataContainer().set(Keys.CUSTOM_MOB, PersistentDataType.STRING, def.id);
            e.setPersistent(true);
            e.setRemoveWhenFarAway(false);
            applyStats(e, def, 1.0, 1.0);
        };
        LivingEntity e = w.spawn(loc, cls, configure);
        MobState st = adopt(e);
        if (st != null) {
            st.bornTick = tickCounter;
            plugin.skills().fire(st, Trigger.ON_SPAWN, null);
            if (st.appliedModel.length() > 0 && !def.animSpawn.isBlank()) {
                plugin.models().animate(e, st.appliedModel, def.animSpawn, false);
                st.animLockUntilTick = tickCounter + lockTicks(def.model, def.animSpawn, 20);
            }
        }
        return e;
    }

    public static Class<? extends LivingEntity> classOf(EntityType type) {
        Class<? extends Entity> c = type.getEntityClass();
        if (c == null || !LivingEntity.class.isAssignableFrom(c) || !type.isSpawnable()
                || type == EntityType.PLAYER) {
            return null;
        }
        return c.asSubclass(LivingEntity.class);
    }

    /** Tipos válidos para mobs custom (vivos y que se pueden hacer aparecer). */
    public static List<EntityType> validTypes() {
        List<EntityType> out = new ArrayList<>();
        for (EntityType t : EntityType.values()) {
            if (t != EntityType.UNKNOWN && classOf(t) != null && t != EntityType.ARMOR_STAND
                    && t != EntityType.GIANT) {
                out.add(t);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- estado

    /** Registra una entidad marcada como mob custom. Devuelve null si no lo es (o si ya no existe su definición). */
    public MobState adopt(Entity entity) {
        if (!(entity instanceof LivingEntity le)) {
            return null;
        }
        String id = entity.getPersistentDataContainer().get(Keys.CUSTOM_MOB, PersistentDataType.STRING);
        if (id == null || id.equals(TEST_ID)) {
            return null;
        }
        MobState existing = active.get(entity.getUniqueId());
        if (existing != null) {
            return existing;
        }
        MobDef def = defs.get(id);
        if (def == null) {
            // Definición borrada mientras el mob estaba descargado: se elimina para no dejar huérfanos.
            for (String m : plugin.models().modelNames()) {
                plugin.models().remove(entity, m);
            }
            entity.remove();
            return null;
        }
        MobState st = new MobState(le, def);
        st.bornTick = tickCounter;
        st.idleSinceTick = tickCounter;
        st.lastLocation = le.getLocation();
        active.put(st.id, st);
        applyStats(le, def, 1.0, 1.0);
        // Restaura la fase según la vida actual.
        updatePhases(st, false);
        applyModel(st);
        ensureBar(st);
        return st;
    }

    /** Elimina el estado de memoria de un mob que se descarga o muere, sin tocar la entidad. */
    public void release(Entity entity) {
        MobState st = active.remove(entity.getUniqueId());
        if (st != null) {
            releaseState(st, false);
        }
    }

    private void releaseState(MobState st, boolean removeModel) {
        hideBar(st);
        if (removeModel && !st.appliedModel.isEmpty()) {
            plugin.models().remove(st.entity, st.appliedModel);
        }
        st.appliedModel = "";
    }

    /** Quita un mob del mundo por completo (modelo, barra, entidad). */
    public void discard(MobState st) {
        active.remove(st.id);
        releaseState(st, true);
        plugin.skills().forgetMob(st);
        plugin.skills().discardSummons(st);
        if (st.entity.isValid()) {
            st.entity.remove();
        }
    }

    /** Elimina todos los mobs custom de las dimensiones/mundos indicados. */
    public void removeInWorld(World w) {
        for (MobState st : new ArrayList<>(active.values())) {
            if (st.entity.getWorld().equals(w)) {
                active.remove(st.id);
                releaseState(st, false);
                plugin.skills().forgetMob(st);
            }
        }
    }

    // ---------------------------------------------------------------- estadísticas

    public void applyStats(LivingEntity e, MobDef d, double damageMul, double speedMul) {
        set(e, Attribute.GENERIC_MAX_HEALTH, d.health);
        set(e, Attribute.GENERIC_ATTACK_DAMAGE, d.damage * damageMul);
        set(e, Attribute.GENERIC_MOVEMENT_SPEED, d.speed * speedMul);
        set(e, Attribute.GENERIC_ARMOR, d.armor);
        set(e, Attribute.GENERIC_ARMOR_TOUGHNESS, d.toughness);
        set(e, Attribute.GENERIC_KNOCKBACK_RESISTANCE, d.knockbackResistance);
        set(e, Attribute.GENERIC_FOLLOW_RANGE, d.followRange);
        set(e, Attribute.GENERIC_SCALE, d.scale);
        AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null && e.getHealth() > max.getValue()) {
            e.setHealth(max.getValue());
        }
        e.customName(Text.mm(d.displayName));
        e.setCustomNameVisible(d.nameVisible);
        e.setGlowing(d.glowing);
        e.setSilent(d.silentVanilla);
        if (d.fireImmune) {
            e.setFireTicks(0);
        }
    }

    /** Aplica la vida completa (solo al aparecer). */
    public void fillHealth(LivingEntity e) {
        AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (max != null) {
            e.setHealth(max.getValue());
        }
    }

    private static void set(LivingEntity e, Attribute a, double value) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) {
            ai.setBaseValue(value);
        }
    }

    // ---------------------------------------------------------------- modelos

    /** Aplica (o reaplica) el modelo configurado. Seguro de llamar varias veces. */
    public void applyModel(MobState st) {
        MobDef d = st.def;
        if (!d.hasModel() || !plugin.models().available()) {
            if (!st.appliedModel.isEmpty()) {
                plugin.models().remove(st.entity, st.appliedModel);
                st.appliedModel = "";
            }
            return;
        }
        if (!st.appliedModel.isEmpty() && !st.appliedModel.equals(d.model)) {
            plugin.models().remove(st.entity, st.appliedModel);
            st.appliedModel = "";
        }
        if (plugin.models().apply(st.entity, d.model, d.modelScale)) {
            st.appliedModel = d.model;
            st.animState = "";
        }
    }

    /** Reaplica los modelos a todos los mobs vivos (se llama cuando BetterModel termina de recargar). */
    public void reapplyModels() {
        for (MobState st : active.values()) {
            st.appliedModel = "";
            applyModel(st);
        }
    }

    /** Refresca todos los mobs vivos de una definición tras editarla. */
    public void refresh(MobDef def) {
        for (MobState st : active.values()) {
            if (st.def == def) {
                applyStats(st.entity, def, st.damageMultiplier, st.speedMultiplier);
                applyModel(st);
                ensureBar(st);
            }
        }
    }

    private long lockTicks(String model, String anim, long fallback) {
        double len = plugin.models().animationLength(model, anim);
        return len > 0 ? (long) Math.ceil(len * 20.0) : fallback;
    }

    /** Reproduce una animación puntual y bloquea idle/walk durante su duración. */
    public void playOnce(MobState st, String anim) {
        if (st.appliedModel.isEmpty() || anim == null || anim.isBlank()) {
            return;
        }
        if (plugin.models().animate(st.entity, st.appliedModel, anim, false)) {
            st.animLockUntilTick = tickCounter + lockTicks(st.appliedModel, anim, 15);
            st.animState = "";
        }
    }

    // ---------------------------------------------------------------- tick

    private void tick() {
        tickCounter += PERIOD;
        for (Iterator<MobState> it = active.values().iterator(); it.hasNext(); ) {
            MobState st = it.next();
            LivingEntity e = st.entity;
            if (!e.isValid() || e.isDead()) {
                if (!st.dying) {
                    it.remove();
                    releaseState(st, false);
                    plugin.skills().forgetMob(st);
                }
                continue;
            }
            try {
                tickMob(st);
            } catch (Exception ex) {
                plugin.getLogger().warning("Error en mob " + st.def.id + ": " + ex);
            }
        }
        // Modelos de prueba caducados
        testEntities.removeIf(e -> !e.isValid());
    }

    private void tickMob(MobState st) {
        MobDef d = st.def;
        LivingEntity e = st.entity;

        // Modelo pendiente (BetterModel carga sus modelos de forma asíncrona)
        if (d.hasModel() && st.appliedModel.isEmpty() && tickCounter % 40 == 0) {
            applyModel(st);
        }
        // Regeneración
        if (d.regenPerSecond > 0) {
            AttributeInstance max = e.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                e.setHealth(Math.min(max.getValue(), e.getHealth() + d.regenPerSecond * PERIOD / 20.0));
            }
        }
        if (d.fireImmune && e.getFireTicks() > 0) {
            e.setFireTicks(0);
        }
        updatePhases(st, true);
        updateBar(st);
        ambient(st);
        updateAnimation(st);
    }

    private void ambient(MobState st) {
        MobDef d = st.def;
        Location loc = st.entity.getLocation();
        if (!d.ambientSound.isBlank() && tickCounter >= st.nextAmbientTick) {
            Fx.sound(loc, d.ambientSound, 1.0f, 1.0f);
            st.nextAmbientTick = tickCounter + d.ambientIntervalSeconds * 20L
                    + ThreadLocalRandom.current().nextInt(40);
        }
        if (d.particleCount > 0 && !d.particle.isBlank()) {
            Particle p = Fx.particle(d.particle, null);
            if (p != null) {
                Fx.spawn(loc.clone().add(0, st.entity.getHeight() / 2, 0), p, d.particleCount, 0.4, null);
            }
        }
    }

    private void updateAnimation(MobState st) {
        if (st.appliedModel.isEmpty() || tickCounter < st.animLockUntilTick) {
            return;
        }
        Location now = st.entity.getLocation();
        boolean moved = st.lastLocation != null && st.lastLocation.getWorld() == now.getWorld()
                && st.lastLocation.distanceSquared(now) > 0.0036;
        st.lastLocation = now;
        if (moved) {
            st.idleSinceTick = tickCounter;
        }
        String desired = moved ? "walk" : "idle";
        if (desired.equals(st.animState)) {
            return;
        }
        String prev = st.animState.equals("walk") ? st.def.animWalk : st.animState.equals("idle") ? st.def.animIdle : "";
        String next = moved ? st.def.animWalk : st.def.animIdle;
        if (!prev.isBlank()) {
            plugin.models().stopAnimation(st.entity, st.appliedModel, prev);
        }
        st.animState = desired;
        if (!next.isBlank()) {
            plugin.models().animate(st.entity, st.appliedModel, next, true);
        }
    }

    // ---------------------------------------------------------------- fases de jefe

    private void updatePhases(MobState st, boolean announce) {
        List<BossPhase> phases = st.def.phases;
        if (phases.isEmpty()) {
            return;
        }
        AttributeInstance max = st.entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = max == null ? st.entity.getHealth() : max.getValue();
        double pct = st.entity.getHealth() / Math.max(1.0, maxHp) * 100.0;
        for (int i = st.phase + 1; i < phases.size(); i++) {
            if (pct <= phases.get(i).healthPercent) {
                enterPhase(st, i, announce);
            } else {
                break;
            }
        }
    }

    private void enterPhase(MobState st, int index, boolean announce) {
        BossPhase p = st.def.phases.get(index);
        st.phase = index;
        // Los multiplicadores de la última fase alcanzada son los vigentes.
        st.damageMultiplier = p.damageMultiplier;
        st.speedMultiplier = p.speedMultiplier;
        applyStats(st.entity, st.def, st.damageMultiplier, st.speedMultiplier);
        if (!announce) {
            return;
        }
        if (p.healFraction > 0) {
            AttributeInstance max = st.entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (max != null) {
                st.entity.setHealth(Math.min(max.getValue(), st.entity.getHealth() + max.getValue() * p.healFraction));
            }
        }
        Location loc = st.entity.getLocation();
        if (!p.message.isBlank()) {
            for (Player pl : loc.getWorld().getNearbyPlayers(loc, 48)) {
                pl.sendMessage(Text.mm(p.message));
            }
        }
        Fx.sound(loc, p.sound, 1.5f, 1.0f);
        playOnce(st, p.animation);
        plugin.skills().onPhaseEntered(st, p);
    }

    /** Habilidades activas ahora: las del mob más las de las fases alcanzadas (con reemplazo si procede). */
    public List<SkillBinding> activeBindings(MobState st) {
        List<SkillBinding> base = new ArrayList<>(st.def.skills);
        List<SkillBinding> extra = new ArrayList<>();
        boolean keepBase = true;
        for (int i = 0; i <= st.phase && i < st.def.phases.size(); i++) {
            BossPhase p = st.def.phases.get(i);
            if (p.replaceBaseSkills) {
                keepBase = false;
                extra.clear();
            }
            extra.addAll(p.skills);
        }
        List<SkillBinding> out = new ArrayList<>();
        if (keepBase) {
            out.addAll(base);
        }
        out.addAll(extra);
        return out;
    }

    // ---------------------------------------------------------------- barra de jefe

    private static BossBar.Color barColor(String s) {
        try {
            return BossBar.Color.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }

    private static BossBar.Overlay barOverlay(String s) {
        try {
            return BossBar.Overlay.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private void ensureBar(MobState st) {
        MobDef d = st.def;
        if (!d.bossBar) {
            hideBar(st);
            return;
        }
        String title = d.bossBarTitle.isBlank() ? d.displayName : d.bossBarTitle;
        if (st.bar == null) {
            st.bar = BossBar.bossBar(Text.mm(title), 1.0f, barColor(d.bossBarColor), barOverlay(d.bossBarStyle));
        } else {
            st.bar.name(Text.mm(title));
            st.bar.color(barColor(d.bossBarColor));
            st.bar.overlay(barOverlay(d.bossBarStyle));
        }
    }

    private void updateBar(MobState st) {
        if (st.bar == null) {
            return;
        }
        AttributeInstance max = st.entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double maxHp = max == null ? 1 : max.getValue();
        st.bar.progress((float) Math.max(0, Math.min(1, st.entity.getHealth() / maxHp)));
        Location loc = st.entity.getLocation();
        double range = st.def.bossBarRange;
        List<UUID> seen = new ArrayList<>();
        for (Player p : st.entity.getWorld().getPlayers()) {
            boolean inRange = p.getLocation().distanceSquared(loc) <= range * range;
            if (inRange) {
                seen.add(p.getUniqueId());
                if (st.viewers.add(p.getUniqueId())) {
                    p.showBossBar(st.bar);
                }
            } else if (st.viewers.remove(p.getUniqueId())) {
                p.hideBossBar(st.bar);
            }
        }
        // Jugadores que se fueron de mundo o se desconectaron
        for (Iterator<UUID> it = st.viewers.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            if (!seen.contains(u)) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.hideBossBar(st.bar);
                }
                it.remove();
            }
        }
    }

    private void hideBar(MobState st) {
        if (st.bar != null) {
            for (UUID u : st.viewers) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) {
                    p.hideBossBar(st.bar);
                }
            }
        }
        st.viewers.clear();
        st.bar = null;
    }

    // ---------------------------------------------------------------- eventos de combate (llamados por MobListener)

    public void onHurt(MobState st) {
        if (!st.def.hurtSound.isBlank()) {
            Fx.sound(st.entity.getLocation(), st.def.hurtSound, 1f, 1f);
        }
        if (tickCounter >= st.animLockUntilTick) {
            playOnce(st, st.def.animHurt);
        }
    }

    public void onAttack(MobState st) {
        playOnce(st, st.def.animAttack);
    }

    /** Muerte: drops propios, XP, sonido, animación de muerte sin dejar modelo huérfano. */
    public void onDeath(MobState st, List<org.bukkit.inventory.ItemStack> drops, Consumer<Integer> setXp) {
        MobDef d = st.def;
        st.dying = true;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (d.replaceVanillaDrops) {
            drops.clear();
        }
        for (DropEntry de : d.drops) {
            if (de.roll(rnd)) {
                int amount = de.rollAmount(rnd);
                if (amount > 0) {
                    drops.add(de.create(amount));
                }
            }
        }
        if (d.xpMax > 0) {
            setXp.accept(d.xpMin >= d.xpMax ? d.xpMin : rnd.nextInt(d.xpMin, d.xpMax + 1));
        }
        Location loc = st.entity.getLocation();
        Fx.sound(loc, d.deathSound, 1f, 1f);
        boolean deathAnim = false;
        if (!st.appliedModel.isEmpty()) {
            String model = st.appliedModel;
            plugin.models().remove(st.entity, model);
            st.appliedModel = "";
            if (!d.animDeath.isBlank() && plugin.models().animations(model).contains(d.animDeath)) {
                deathAnim = plugin.models().playDeath(loc, model, d.animDeath, d.modelScale);
            }
        }
        if (deathAnim) {
            st.entity.setInvisible(true);
        }
        plugin.skills().fire(st, Trigger.ON_DEATH, null);
        hideBar(st);
        active.remove(st.id);
        plugin.skills().forgetMob(st);
    }

    // ---------------------------------------------------------------- apariciones naturales

    /** Evalúa las reglas de aparición. Devuelve true si el evento original debe cancelarse (modo REPLACE). */
    public boolean onNaturalSpawn(LivingEntity spawned, Location loc) {
        if (defs.isEmpty()) {
            return false;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        World w = loc.getWorld();
        Block block = loc.getBlock();
        String biome = block.getBiome().getKey().getKey();
        boolean cancel = false;
        for (MobDef d : defs.values()) {
            if (!d.enabled || d.spawnRules.isEmpty()) {
                continue;
            }
            for (SpawnRule r : d.spawnRules) {
                String dim = r.dimension.isBlank() ? d.dimension : r.dimension;
                if (!dim.isBlank() && !dim.equals(w.getName())) {
                    continue;
                }
                if (loc.getBlockY() < r.minY || loc.getBlockY() > r.maxY) {
                    continue;
                }
                if (!r.biomes.isEmpty() && r.biomes.stream().noneMatch(b -> b.equalsIgnoreCase(biome))) {
                    continue;
                }
                if (r.mode == SpawnRule.Mode.REPLACE) {
                    if (spawned.getType() != d.type()) {
                        continue;
                    }
                } else if (!(spawned instanceof org.bukkit.entity.Monster)) {
                    continue;
                }
                if (rnd.nextDouble() * 100.0 >= r.chance) {
                    continue;
                }
                if (countInChunk(d, loc.getChunk()) >= r.maxPerChunk) {
                    continue;
                }
                int group = r.groupMin >= r.groupMax ? r.groupMin : rnd.nextInt(r.groupMin, r.groupMax + 1);
                Location where = loc.clone();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < group; i++) {
                        Location l = where.clone().add(rnd.nextDouble(-2, 2), 0, rnd.nextDouble(-2, 2));
                        l = safeSpot(l);
                        if (l != null) {
                            LivingEntity le = spawn(d, l);
                            if (le != null) {
                                fillHealth(le);
                            }
                        }
                    }
                });
                if (r.mode == SpawnRule.Mode.REPLACE) {
                    cancel = true;
                }
            }
        }
        return cancel;
    }

    private Location safeSpot(Location l) {
        Block b = l.getBlock();
        if (b.isPassable() && b.getRelative(0, 1, 0).isPassable()) {
            return l;
        }
        return null;
    }

    private int countInChunk(MobDef d, Chunk c) {
        int n = 0;
        for (MobState st : active.values()) {
            if (st.def == d && st.entity.getChunk().equals(c)) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- prueba de modelos

    /**
     * Genera un mob de prueba (sin IA, invulnerable, no persistente) con el modelo indicado y recorre sus animaciones.
     * Desaparece solo al terminar.
     */
    public boolean testModel(Player viewer, String model, double scale) {
        if (!plugin.models().hasModel(model)) {
            return false;
        }
        Location loc = viewer.getLocation().add(viewer.getLocation().getDirection().setY(0).normalize().multiply(3));
        LivingEntity e = viewer.getWorld().spawn(loc, org.bukkit.entity.Zombie.class, z -> {
            z.setAI(false);
            z.setInvulnerable(true);
            z.setSilent(true);
            z.setPersistent(false);
            z.setShouldBurnInDay(false);
            z.setBaby(false);
            z.getPersistentDataContainer().set(Keys.CUSTOM_MOB, PersistentDataType.STRING, TEST_ID);
        });
        testEntities.add(e);
        if (!plugin.models().apply(e, model, scale)) {
            removeTest(e);
            return false;
        }
        List<String> anims = new ArrayList<>(plugin.models().animations(model));
        long delay = 10;
        for (String a : anims) {
            final String anim = a;
            double len = plugin.models().animationLength(model, a);
            long dur = len > 0 ? (long) Math.min(100, Math.ceil(len * 20)) + 10 : 50;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (e.isValid()) {
                    plugin.models().animate(e, model, anim, false);
                    viewer.sendActionBar(Text.mm("<yellow>Animación: <white>" + Text.escape(anim)));
                }
            }, delay);
            delay += dur;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> removeTest(e), Math.max(100, delay + 20));
        return true;
    }

    private void removeTest(LivingEntity e) {
        testEntities.remove(e);
        for (String m : plugin.models().modelNames()) {
            plugin.models().remove(e, m);
        }
        if (e.isValid()) {
            e.remove();
        }
    }
}
