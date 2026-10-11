package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.ChunkKey;
import me.dimensionforge.util.Keys;
import me.dimensionforge.util.YamlIO;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Registro de menas custom: definiciones, bloques identificados por chunk, agotadas y modelos. */
public final class OreManager {

    public record BlockKey(String world, int x, int y, int z) {
        public static BlockKey of(Block b) {
            return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        }

        public ChunkKey chunk() {
            return ChunkKey.ofBlock(world, x, z);
        }
    }

    public record Depleted(String oreId, long respawnAt) {
    }

    private final DimensionForgePlugin plugin;
    private final File oresFile;
    private final File respawnFile;
    private final Map<String, OreDef> ores = new LinkedHashMap<>();
    private final Map<ChunkKey, Map<Integer, String>> chunkOres = new HashMap<>();
    private final ConcurrentHashMap<ChunkKey, ConcurrentHashMap<Integer, String>> pending = new ConcurrentHashMap<>();
    private final Map<BlockKey, Depleted> depleted = new HashMap<>();
    private final Map<BlockKey, Object> displays = new HashMap<>();
    private final ArrayDeque<BlockKey> displayQueue = new ArrayDeque<>();
    private volatile Map<String, List<OreDef>> byWorld = Collections.emptyMap();
    private boolean respawnDirty;
    private long lastRespawnSave;
    private int tickCounter;

    public OreManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        this.oresFile = new File(plugin.getDataFolder(), "ores.yml");
        this.respawnFile = new File(plugin.getDataFolder(), "ore_respawns.yml");
    }

    // ---------------------------------------------------------------- persistencia

    public void load() {
        ores.clear();
        YamlConfiguration yaml = YamlIO.load(plugin, oresFile);
        ConfigurationSection root = yaml.getConfigurationSection("ores");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) {
                    ores.put(id, OreDef.load(id, s));
                }
            }
        }
        depleted.clear();
        YamlConfiguration r = YamlIO.load(plugin, respawnFile);
        for (String line : r.getStringList("depleted")) {
            String[] p = line.split(";");
            if (p.length == 6) {
                try {
                    depleted.put(new BlockKey(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])),
                            new Depleted(p[4], Long.parseLong(p[5])));
                } catch (NumberFormatException ignored) {
                    // línea corrupta, se descarta
                }
            }
        }
        rebuildSnapshot();
        // chunks ya cargados (recarga del plugin)
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) {
                onChunkLoad(c);
            }
        }
    }

    public void save() {
        YamlIO.saveAsync(plugin, oresFile, oresYaml());
    }

    private YamlConfiguration oresYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (OreDef o : ores.values()) {
            o.save(yaml.createSection("ores." + o.id));
        }
        return yaml;
    }

    private YamlConfiguration respawnYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<String> lines = new ArrayList<>();
        for (Map.Entry<BlockKey, Depleted> e : depleted.entrySet()) {
            BlockKey k = e.getKey();
            lines.add(k.world() + ";" + k.x() + ";" + k.y() + ";" + k.z() + ";" + e.getValue().oreId() + ";" + e.getValue().respawnAt());
        }
        yaml.set("depleted", lines);
        return yaml;
    }

    public void shutdown() {
        YamlIO.saveSync(plugin, oresFile, oresYaml());
        YamlIO.saveSync(plugin, respawnFile, respawnYaml());
        for (Object h : new ArrayList<>(displays.values())) {
            closeHandle(h);
        }
        displays.clear();
    }

    private void rebuildSnapshot() {
        Map<String, List<OreDef>> map = new HashMap<>();
        for (OreDef o : ores.values()) {
            if (o.enabled && o.generate) {
                map.computeIfAbsent(o.dimension, k -> new ArrayList<>()).add(o);
            }
        }
        byWorld = map;
    }

    // ---------------------------------------------------------------- definiciones

    public Collection<OreDef> all() {
        return Collections.unmodifiableCollection(ores.values());
    }

    public OreDef get(String id) {
        return id == null ? null : ores.get(id.toLowerCase());
    }

    public List<String> ids() {
        return new ArrayList<>(ores.keySet());
    }

    public List<OreDef> forDimension(String dim) {
        List<OreDef> out = new ArrayList<>();
        for (OreDef o : ores.values()) {
            if (o.dimension.equals(dim)) {
                out.add(o);
            }
        }
        return out;
    }

    /** Menas con generación activa para un mundo (seguro entre hilos). */
    public List<OreDef> generatorsFor(String world) {
        List<OreDef> l = byWorld.get(world);
        return l == null ? Collections.emptyList() : l;
    }

    public OreDef create(String dimension, String id, Material base) {
        OreDef o = new OreDef(id);
        o.dimension = dimension;
        o.baseMaterial = base.name();
        o.drops.add(new DropEntry());
        ores.put(id, o);
        changed();
        return o;
    }

    public void changed() {
        rebuildSnapshot();
        save();
    }

    public void remove(OreDef o) {
        purge(o.id);
        ores.remove(o.id);
        changed();
    }

    public void removeByDimension(String dim) {
        for (OreDef o : forDimension(dim)) {
            purge(o.id);
            ores.remove(o.id);
        }
        changed();
    }

    /** Olvida todas las posiciones registradas de una mena (los bloques quedan como bloques vanilla). */
    public void purge(String oreId) {
        for (Map.Entry<ChunkKey, Map<Integer, String>> e : new ArrayList<>(chunkOres.entrySet())) {
            boolean changed = e.getValue().values().removeIf(v -> v.equals(oreId));
            if (changed) {
                World w = Bukkit.getWorld(e.getKey().world());
                if (w != null && w.isChunkLoaded(e.getKey().x(), e.getKey().z())) {
                    writePdc(w.getChunkAt(e.getKey().x(), e.getKey().z()), e.getValue());
                }
            }
        }
        for (Iterator<Map.Entry<BlockKey, Object>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockKey, Object> e = it.next();
            OreDef at = oreAt(e.getKey());
            if (at == null || at.id.equals(oreId)) {
                closeHandle(e.getValue());
                it.remove();
            }
        }
        depleted.values().removeIf(d -> d.oreId().equals(oreId));
        respawnDirty = true;
    }

    // ---------------------------------------------------------------- identificación por chunk

    public static int pack(int x, int y, int z) {
        return ((y + 4096) << 8) | ((x & 15) << 4) | (z & 15);
    }

    private static int unpackY(int p) {
        return (p >> 8) - 4096;
    }

    /** Mena registrada en este bloque (aunque esté agotada). */
    public OreDef oreAt(Block b) {
        return oreAt(BlockKey.of(b));
    }

    public OreDef oreAt(BlockKey k) {
        Map<Integer, String> m = chunkOres.get(k.chunk());
        if (m == null) {
            return null;
        }
        String id = m.get(pack(k.x(), k.y(), k.z()));
        return id == null ? null : ores.get(id);
    }

    /** Mena activa (bloque base presente y no agotada). */
    public OreDef activeAt(Block b) {
        OreDef o = oreAt(b);
        if (o == null || !o.enabled) {
            return null;
        }
        if (depleted.containsKey(BlockKey.of(b))) {
            return null;
        }
        return b.getType() == o.base() ? o : null;
    }

    public boolean isDepleted(Block b) {
        return depleted.containsKey(BlockKey.of(b));
    }

    public boolean isOrePosition(Block b) {
        return oreAt(b) != null;
    }

    public void register(Block b, String oreId) {
        BlockKey k = BlockKey.of(b);
        Map<Integer, String> m = chunkOres.computeIfAbsent(k.chunk(), key -> new HashMap<>());
        m.put(pack(k.x(), k.y(), k.z()), oreId);
        writePdc(b.getChunk(), m);
    }

    public void unregister(Block b) {
        BlockKey k = BlockKey.of(b);
        Map<Integer, String> m = chunkOres.get(k.chunk());
        if (m != null && m.remove(pack(k.x(), k.y(), k.z())) != null) {
            writePdc(b.getChunk(), m);
        }
        removeDisplay(k);
        if (depleted.remove(k) != null) {
            respawnDirty = true;
        }
    }

    private void writePdc(Chunk c, Map<Integer, String> m) {
        PersistentDataContainer pdc = c.getPersistentDataContainer();
        if (m.isEmpty()) {
            pdc.remove(Keys.CHUNK_ORES);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, String> e : m.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(e.getKey()).append(':').append(e.getValue());
        }
        pdc.set(Keys.CHUNK_ORES, PersistentDataType.STRING, sb.toString());
    }

    private Map<Integer, String> readPdc(Chunk c) {
        Map<Integer, String> m = new HashMap<>();
        String s = c.getPersistentDataContainer().get(Keys.CHUNK_ORES, PersistentDataType.STRING);
        if (s == null || s.isEmpty()) {
            return m;
        }
        for (String part : s.split(";")) {
            int idx = part.indexOf(':');
            if (idx > 0) {
                try {
                    m.put(Integer.parseInt(part.substring(0, idx)), part.substring(idx + 1));
                } catch (NumberFormatException ignored) {
                    // entrada corrupta
                }
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- eventos de chunk

    public void onChunkLoad(Chunk c) {
        ChunkKey key = ChunkKey.of(c);
        Map<Integer, String> m = readPdc(c);
        chunkOres.put(key, m);
        flushPendingFor(c, key);
        queueDisplays(c, key);
        restoreDueIn(key);
    }

    public void onChunkUnload(Chunk c) {
        ChunkKey key = ChunkKey.of(c);
        chunkOres.remove(key);
        for (Iterator<Map.Entry<BlockKey, Object>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockKey, Object> e = it.next();
            if (e.getKey().chunk().equals(key)) {
                closeHandle(e.getValue());
                it.remove();
            }
        }
        displayQueue.removeIf(k -> k.chunk().equals(key));
    }

    public void onWorldUnload(World w) {
        String name = w.getName();
        chunkOres.keySet().removeIf(k -> k.world().equals(name));
        pending.keySet().removeIf(k -> k.world().equals(name));
        for (Iterator<Map.Entry<BlockKey, Object>> it = displays.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockKey, Object> e = it.next();
            if (e.getKey().world().equals(name)) {
                closeHandle(e.getValue());
                it.remove();
            }
        }
        displayQueue.removeIf(k -> k.world().equals(name));
    }

    private void queueDisplays(Chunk c, ChunkKey key) {
        Map<Integer, String> m = chunkOres.get(key);
        if (m == null || m.isEmpty()) {
            return;
        }
        for (Map.Entry<Integer, String> e : m.entrySet()) {
            OreDef o = ores.get(e.getValue());
            if (o == null || o.appearance != OreDef.Appearance.MODEL) {
                continue;
            }
            int p = e.getKey();
            BlockKey bk = new BlockKey(key.world(), (key.x() << 4) + ((p >> 4) & 15), unpackY(p), (key.z() << 4) + (p & 15));
            if (!depleted.containsKey(bk)) {
                displayQueue.add(bk);
            }
        }
    }

    // ---------------------------------------------------------------- generación (pendientes)

    /** Registra posiciones generadas por el populator (puede llamarse desde cualquier hilo). */
    public void registerGenerated(String world, int cx, int cz, Map<Integer, String> positions) {
        if (positions.isEmpty()) {
            return;
        }
        pending.computeIfAbsent(new ChunkKey(world, cx, cz), k -> new ConcurrentHashMap<>()).putAll(positions);
    }

    private void flushPendingFor(Chunk c, ChunkKey key) {
        ConcurrentHashMap<Integer, String> p = pending.remove(key);
        if (p == null || p.isEmpty()) {
            return;
        }
        Map<Integer, String> m = chunkOres.computeIfAbsent(key, k -> new HashMap<>());
        m.putAll(p);
        writePdc(c, m);
        queueDisplays(c, key);
    }

    private void flushPending() {
        if (pending.isEmpty()) {
            return;
        }
        int budget = 16;
        for (ChunkKey key : new ArrayList<>(pending.keySet())) {
            World w = Bukkit.getWorld(key.world());
            if (w == null) {
                pending.remove(key);
                continue;
            }
            if (w.isChunkLoaded(key.x(), key.z())) {
                flushPendingFor(w.getChunkAt(key.x(), key.z()), key);
                if (--budget <= 0) {
                    break;
                }
            }
        }
    }

    public void onWorldLoaded(World w) {
        for (var pop : w.getPopulators()) {
            if (pop instanceof OrePopulator) {
                return;
            }
        }
        w.getPopulators().add(new OrePopulator(this));
    }

    /** Genera menas en un chunk ya cargado (comando regen). Devuelve cuántos bloques se colocaron. */
    public int regenerate(Chunk c) {
        World w = c.getWorld();
        Random rnd = new Random(w.getSeed() ^ ((long) c.getX() * 341873128712L) ^ ((long) c.getZ() * 132897987541L) ^ System.nanoTime());
        Map<Integer, String> placed = new HashMap<>();
        OreGenerator.generate(w, w.getName(), w.getMinHeight(), w.getMaxHeight(), rnd, c.getX(), c.getZ(), generatorsFor(w.getName()), placed);
        if (placed.isEmpty()) {
            return 0;
        }
        ChunkKey key = ChunkKey.of(c);
        Map<Integer, String> m = chunkOres.computeIfAbsent(key, k -> new HashMap<>());
        m.putAll(placed);
        writePdc(c, m);
        queueDisplays(c, key);
        return placed.size();
    }

    // ---------------------------------------------------------------- agotadas y respawn

    public void deplete(Block b, OreDef ore) {
        BlockKey k = BlockKey.of(b);
        removeDisplay(k);
        b.setType(ore.depleted(), false);
        depleted.put(k, new Depleted(ore.id, System.currentTimeMillis() + ore.respawnSeconds * 1000L));
        respawnDirty = true;
    }

    private void restoreDueIn(ChunkKey key) {
        long now = System.currentTimeMillis();
        for (Map.Entry<BlockKey, Depleted> e : new ArrayList<>(depleted.entrySet())) {
            if (e.getKey().chunk().equals(key) && e.getValue().respawnAt() <= now) {
                restore(e.getKey(), e.getValue());
            }
        }
    }

    private void restore(BlockKey k, Depleted d) {
        World w = Bukkit.getWorld(k.world());
        if (w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4)) {
            return;
        }
        OreDef o = ores.get(d.oreId());
        depleted.remove(k);
        respawnDirty = true;
        Block b = w.getBlockAt(k.x(), k.y(), k.z());
        if (o == null) {
            return;
        }
        // si alguien cambió el bloque mientras estaba agotado, no lo pisamos
        if (b.getType() == o.depleted() || b.getType().isAir()) {
            b.setType(o.base(), false);
            if (o.appearance == OreDef.Appearance.MODEL) {
                displayQueue.add(k);
            }
        } else {
            unregister(b);
        }
    }

    /** Tarea central (cada tick): cola de modelos, respawns y guardado diferido. */
    public void tick() {
        int budget = plugin.getConfig().getInt("ores.displays-per-tick", 20);
        while (budget-- > 0 && !displayQueue.isEmpty()) {
            BlockKey k = displayQueue.poll();
            World w = Bukkit.getWorld(k.world());
            if (w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4)) {
                continue;
            }
            Block b = w.getBlockAt(k.x(), k.y(), k.z());
            OreDef o = oreAt(k);
            if (o != null && b.getType() == o.base() && !depleted.containsKey(k)) {
                spawnDisplay(b, o);
            }
        }
        if (++tickCounter % 20 != 0) {
            return;
        }
        long now = System.currentTimeMillis();
        int limit = plugin.getConfig().getInt("ores.respawns-per-second", 40);
        for (Map.Entry<BlockKey, Depleted> e : new ArrayList<>(depleted.entrySet())) {
            if (limit <= 0) {
                break;
            }
            if (e.getValue().respawnAt() <= now) {
                restore(e.getKey(), e.getValue());
                limit--;
            }
        }
        flushPending();
        if (respawnDirty && now - lastRespawnSave > 5000) {
            respawnDirty = false;
            lastRespawnSave = now;
            YamlIO.saveAsync(plugin, respawnFile, respawnYaml());
        }
    }

    // ---------------------------------------------------------------- modelos sobre el bloque

    public void spawnDisplay(Block b, OreDef ore) {
        BlockKey k = BlockKey.of(b);
        removeDisplay(k);
        String model = ore.modelId();
        Location center = b.getLocation().add(0.5, 0.5, 0.5);
        if (plugin.models().hasModel(model)) {
            AutoCloseable h = plugin.models().spawnStatic(center, model, 1.0f);
            if (h != null) {
                displays.put(k, h);
                return;
            }
        }
        ItemStack item = plugin.packs().cmds().item("ore", model);
        if (item == null) {
            return;
        }
        float scale = (float) plugin.getConfig().getDouble("ores.display-scale", 1.002);
        ItemDisplay d = b.getWorld().spawn(center, ItemDisplay.class, display -> {
            display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f()));
            display.setBillboard(Display.Billboard.FIXED);
            display.setPersistent(false);
            display.setViewRange(1.0f);
            display.getPersistentDataContainer().set(Keys.ORE_DISPLAY, PersistentDataType.STRING, ore.id);
        });
        displays.put(k, d.getUniqueId());
    }

    public void removeDisplay(BlockKey k) {
        Object h = displays.remove(k);
        if (h != null) {
            closeHandle(h);
        }
    }

    private void closeHandle(Object h) {
        try {
            if (h instanceof UUID id) {
                Entity e = Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
            } else if (h instanceof AutoCloseable ac) {
                ac.close();
            }
        } catch (Exception ex) {
            plugin.getLogger().fine("No se pudo cerrar un modelo de mena: " + ex);
        }
    }

    /** Reaplica los modelos de una mena (tras cambiar su modelo) en los chunks cargados. */
    public void refreshDisplays(OreDef ore) {
        for (Map.Entry<ChunkKey, Map<Integer, String>> e : chunkOres.entrySet()) {
            for (Map.Entry<Integer, String> en : e.getValue().entrySet()) {
                if (en.getValue().equals(ore.id)) {
                    int p = en.getKey();
                    BlockKey bk = new BlockKey(e.getKey().world(), (e.getKey().x() << 4) + ((p >> 4) & 15), unpackY(p),
                            (e.getKey().z() << 4) + (p & 15));
                    removeDisplay(bk);
                    if (ore.appearance == OreDef.Appearance.MODEL && !depleted.containsKey(bk)) {
                        displayQueue.add(bk);
                    }
                }
            }
        }
    }

    /** Reaplica los modelos de todas las menas (cuando BetterModel termina de recargar sus modelos). */
    public void refreshAllDisplays() {
        for (OreDef o : ores.values()) {
            if (o.appearance == OreDef.Appearance.MODEL) {
                refreshDisplays(o);
            }
        }
    }

    /** Escanea los chunks cargados de la dimensión de la mena y cuenta sus bloques. */
    public int[] scan(OreDef ore) {
        int total = 0;
        int missing = 0;
        World w = Bukkit.getWorld(ore.dimension);
        if (w == null) {
            return new int[]{0, 0};
        }
        for (Map.Entry<ChunkKey, Map<Integer, String>> e : chunkOres.entrySet()) {
            if (!e.getKey().world().equals(ore.dimension)) {
                continue;
            }
            for (Map.Entry<Integer, String> en : e.getValue().entrySet()) {
                if (!en.getValue().equals(ore.id)) {
                    continue;
                }
                total++;
                int p = en.getKey();
                BlockKey bk = new BlockKey(e.getKey().world(), (e.getKey().x() << 4) + ((p >> 4) & 15), unpackY(p),
                        (e.getKey().z() << 4) + (p & 15));
                Block b = w.getBlockAt(bk.x(), bk.y(), bk.z());
                if (!depleted.containsKey(bk) && b.getType() != ore.base()) {
                    missing++;
                } else if (ore.appearance == OreDef.Appearance.MODEL && !displays.containsKey(bk) && !depleted.containsKey(bk)) {
                    displayQueue.add(bk);
                }
            }
        }
        return new int[]{total, missing};
    }
}
