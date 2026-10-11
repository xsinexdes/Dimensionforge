package me.dimensionforge.portal;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.dimension.DimensionData;
import me.dimensionforge.util.ChunkKey;
import me.dimensionforge.util.Fx;
import me.dimensionforge.util.Text;
import me.dimensionforge.util.VaultHook;
import me.dimensionforge.util.YamlIO;
import net.kyori.adventure.title.Title;
import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Gestiona los portales: persistencia, índice por chunk, relleno visual y teletransporte. */
public final class PortalManager {

    private static final class FillJob {
        final Portal portal;
        final boolean clear;
        final BlockData data;
        final String clearMaterial;
        int x, y, z;

        FillJob(Portal portal, boolean clear, BlockData data, String clearMaterial) {
            this.portal = portal;
            this.clear = clear;
            this.data = data;
            this.clearMaterial = clearMaterial;
            this.x = portal.x1;
            this.y = portal.y1;
            this.z = portal.z1;
        }
    }

    private final DimensionForgePlugin plugin;
    private final File file;
    private final Map<String, Portal> portals = new LinkedHashMap<>();
    private final Map<ChunkKey, List<Portal>> index = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final ArrayDeque<FillJob> jobs = new ArrayDeque<>();
    private VaultHook vault;
    private boolean warnedVault;

    public PortalManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    // ---------------------------------------------------------------- persistencia

    public void load() {
        vault = new VaultHook();
        portals.clear();
        YamlConfiguration yaml = YamlIO.load(plugin, file);
        ConfigurationSection root = yaml.getConfigurationSection("portals");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s != null) {
                    portals.put(id, Portal.load(id, s));
                }
            }
        }
        rebuildIndex();
    }

    public void save() {
        YamlIO.saveAsync(plugin, file, toYaml());
    }

    public void saveSync() {
        YamlIO.saveSync(plugin, file, toYaml());
    }

    private YamlConfiguration toYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Portal p : portals.values()) {
            p.save(yaml.createSection("portals." + p.id));
        }
        return yaml;
    }

    public void rebuildIndex() {
        index.clear();
        for (Portal p : portals.values()) {
            for (int cx = p.x1 >> 4; cx <= p.x2 >> 4; cx++) {
                for (int cz = p.z1 >> 4; cz <= p.z2 >> 4; cz++) {
                    index.computeIfAbsent(new ChunkKey(p.world, cx, cz), k -> new ArrayList<>()).add(p);
                }
            }
        }
    }

    // ---------------------------------------------------------------- consulta

    public Collection<Portal> all() {
        return Collections.unmodifiableCollection(portals.values());
    }

    public Portal get(String id) {
        return id == null ? null : portals.get(id.toLowerCase());
    }

    public List<String> ids() {
        return new ArrayList<>(portals.keySet());
    }

    public Portal findAt(String world, int x, int y, int z) {
        List<Portal> list = index.get(ChunkKey.ofBlock(world, x, z));
        if (list == null) {
            return null;
        }
        for (Portal p : list) {
            if (p.contains(world, x, y, z)) {
                return p;
            }
        }
        return null;
    }

    /** Portal cuyo relleno protegido ocupa este bloque. */
    public boolean isProtectedFill(Block b) {
        Portal p = findAt(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        return p != null && p.protectFill && !p.filledMaterial.isEmpty() && b.getType().name().equals(p.filledMaterial)
                && plugin.getConfig().getBoolean("portals.protect-fill", true);
    }

    public String validateId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,32}")) {
            return "error.portal-id-invalid";
        }
        if (portals.containsKey(id)) {
            return "error.portal-id-exists";
        }
        return null;
    }

    public void add(Portal p) {
        portals.put(p.id, p);
        rebuildIndex();
        save();
    }

    public void remove(Portal p) {
        clearFill(p);
        portals.remove(p.id);
        for (Portal other : portals.values()) {
            if (p.id.equals(other.returnPortal)) {
                other.returnPortal = "";
            }
        }
        rebuildIndex();
        save();
    }

    // ---------------------------------------------------------------- relleno visual

    /** Quita el relleno anterior y coloca el nuevo según la configuración del portal. */
    public boolean refill(Portal p) {
        clearFill(p);
        if (p.fillMode == Portal.FillMode.NONE) {
            p.filledMaterial = "";
            return true;
        }
        World w = Bukkit.getWorld(p.world);
        if (w == null) {
            return false;
        }
        long max = plugin.getConfig().getLong("portals.max-fill-volume", 4096);
        if (p.volume() > max) {
            return false;
        }
        BlockData data = fillData(p);
        if (data == null) {
            return false;
        }
        p.filledMaterial = data.getMaterial().name();
        jobs.add(new FillJob(p, false, data, null));
        return true;
    }

    public void clearFill(Portal p) {
        if (p.filledMaterial == null || p.filledMaterial.isEmpty()) {
            return;
        }
        jobs.add(new FillJob(p, true, null, p.filledMaterial));
        p.filledMaterial = "";
    }

    private BlockData fillData(Portal p) {
        switch (p.fillMode) {
            case END_GATEWAY:
                return Material.END_GATEWAY.createBlockData();
            case NETHER_PORTAL: {
                BlockData bd = Material.NETHER_PORTAL.createBlockData();
                if (bd instanceof Orientable o) {
                    o.setAxis((p.x2 - p.x1) >= (p.z2 - p.z1) ? Axis.X : Axis.Z);
                }
                return bd;
            }
            case BLOCK: {
                Material m = Material.matchMaterial(p.fillMaterial == null ? "" : p.fillMaterial);
                if (m == null || !m.isBlock() || m.isAir()) {
                    return null;
                }
                return m.createBlockData();
            }
            default:
                return null;
        }
    }

    /** Procesa los trabajos de relleno de forma limitada por tick. */
    public void tickFill() {
        int budget = plugin.getConfig().getInt("portals.fill-blocks-per-tick", 600);
        boolean onlyAir = plugin.getConfig().getBoolean("portals.fill-only-air", true);
        while (budget > 0 && !jobs.isEmpty()) {
            FillJob job = jobs.peek();
            World w = Bukkit.getWorld(job.portal.world);
            if (w == null) {
                jobs.poll();
                continue;
            }
            Portal p = job.portal;
            boolean finished = false;
            while (budget > 0) {
                Block b = w.getBlockAt(job.x, job.y, job.z);
                if (job.clear) {
                    if (b.getType().name().equals(job.clearMaterial)) {
                        b.setType(Material.AIR, false);
                    }
                } else if (!onlyAir || b.getType().isAir()) {
                    b.setBlockData(job.data, false);
                }
                budget--;
                if (++job.z > p.z2) {
                    job.z = p.z1;
                    if (++job.y > p.y2) {
                        job.y = p.y1;
                        if (++job.x > p.x2) {
                            finished = true;
                            break;
                        }
                    }
                }
            }
            if (finished) {
                jobs.poll();
            }
        }
    }

    /** Partículas ambientales de los portales con jugadores cerca. */
    public void tickParticles() {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        for (Portal p : portals.values()) {
            if (!p.particles) {
                continue;
            }
            World w = Bukkit.getWorld(p.world);
            if (w == null || w.getPlayers().isEmpty()) {
                continue;
            }
            double cx = p.centerX();
            double cz = p.centerZ();
            double cy = (p.y1 + p.y2 + 1) / 2.0;
            boolean near = false;
            for (Player pl : w.getPlayers()) {
                Location l = pl.getLocation();
                double dx = l.getX() - cx;
                double dz = l.getZ() - cz;
                if (dx * dx + dz * dz < 48 * 48 && Math.abs(l.getY() - cy) < 48) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                continue;
            }
            Particle part = Fx.particle(p.particleType, Particle.PORTAL);
            for (int i = 0; i < 6; i++) {
                double x = p.x1 + rnd.nextDouble() * (p.x2 - p.x1 + 1);
                double y = p.y1 + rnd.nextDouble() * (p.y2 - p.y1 + 1);
                double z = p.z1 + rnd.nextDouble() * (p.z2 - p.z1 + 1);
                w.spawnParticle(part, x, y, z, 1, 0.1, 0.1, 0.1, 0.02);
            }
        }
    }

    // ---------------------------------------------------------------- teletransporte

    /** Resuelve un destino: dimensión administrada o cualquier mundo cargado. */
    public Location resolveArrival(Portal p) {
        World w;
        DimensionData d = plugin.dimensions().get(p.destination);
        if (d != null) {
            w = plugin.dimensions().ensureLoaded(d);
            if (w == null) {
                return null;
            }
            if (p.arrivalSpawn) {
                return plugin.dimensions().spawnOf(d);
            }
        } else {
            w = Bukkit.getWorld(p.destination);
            if (w == null) {
                return null;
            }
            if (p.arrivalSpawn) {
                return w.getSpawnLocation().add(0.5, 0, 0.5);
            }
        }
        return new Location(w, p.ax, p.ay, p.az, p.ayaw, p.apitch);
    }

    public void use(Player player, Portal p) {
        try {
            long now = System.currentTimeMillis();
            Long grace = graceUntil.get(player.getUniqueId());
            if (grace != null && now < grace) {
                return;
            }
            Map<String, Long> cd = cooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
            Long until = cd.get(p.id);
            if (until != null && now < until) {
                plugin.messages().send(player, "portal.cooldown", "seconds", (until - now + 999) / 1000);
                return;
            }
            if (!p.permission.isBlank() && !player.hasPermission(p.permission) && !player.hasPermission("dimensionforge.bypass")) {
                plugin.messages().send(player, "portal.no-permission");
                return;
            }
            Location dest = resolveArrival(p);
            if (dest == null) {
                plugin.messages().send(player, "portal.destination-unavailable", "destination", p.destination);
                return;
            }
            DimensionData dd = plugin.dimensions().get(p.destination);
            if (dd != null && !dd.permission.isBlank() && !player.hasPermission(dd.permission)
                    && !player.hasPermission("dimensionforge.bypass")) {
                plugin.messages().send(player, "dimension.no-permission", "name", dd.name);
                return;
            }
            if (!charge(player, p)) {
                return;
            }
            final Location from = player.getLocation().clone();
            player.teleportAsync(dest).thenAccept(ok -> {
                if (!ok) {
                    return;
                }
                long t = System.currentTimeMillis();
                cd.put(p.id, t + p.cooldown * 1000L);
                int grace2 = plugin.getConfig().getInt("portals.arrival-grace-seconds", 2);
                graceUntil.put(player.getUniqueId(), t + grace2 * 1000L);
                Fx.sound(from, p.sound, 1f, 1f);
                Fx.sound(dest, p.sound, 1f, 1f);
                if (!p.message.isBlank()) {
                    player.sendMessage(Text.mm(p.message));
                }
                if (!p.title.isBlank()) {
                    String[] parts = p.title.split("\\|", 2);
                    player.showTitle(Title.title(Text.mm(parts[0]), Text.mm(parts.length > 1 ? parts[1] : "")));
                }
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Error usando el portal " + p.id + ": " + ex);
        }
    }

    private boolean charge(Player player, Portal p) {
        if (player.hasPermission("dimensionforge.bypass")) {
            return true;
        }
        Material mat = p.costMaterial.isBlank() ? null : Material.matchMaterial(p.costMaterial);
        if (mat != null && p.costAmount > 0) {
            ItemStack need = new ItemStack(mat, 1);
            if (!player.getInventory().containsAtLeast(need, p.costAmount)) {
                plugin.messages().send(player, "portal.cost-item", "amount", p.costAmount, "item", mat.name());
                return false;
            }
        }
        boolean money = p.costMoney > 0;
        if (money) {
            if (!vault.available()) {
                if (!warnedVault) {
                    warnedVault = true;
                    plugin.getLogger().warning("Un portal tiene costo en dinero pero no hay Vault/economía; se ignora el costo en dinero.");
                }
                money = false;
            } else if (!vault.has(player, p.costMoney)) {
                plugin.messages().send(player, "portal.cost-money", "amount", p.costMoney);
                return false;
            }
        }
        if (mat != null && p.costAmount > 0) {
            player.getInventory().removeItem(new ItemStack(mat, p.costAmount));
        }
        if (money && !vault.withdraw(player, p.costMoney)) {
            plugin.messages().send(player, "portal.cost-money", "amount", p.costMoney);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- portal de regreso

    /** Crea automáticamente un portal en el destino que lleva de vuelta al portal de origen. */
    public Portal createReturn(Portal src) {
        Location arrival = resolveArrival(src);
        if (arrival == null || arrival.getWorld() == null) {
            return null;
        }
        World dw = arrival.getWorld();
        String rid = src.id + "_return";
        int n = 2;
        while (portals.containsKey(rid)) {
            rid = src.id + "_return" + n++;
        }
        Portal r = new Portal(rid);
        int bx = arrival.getBlockX();
        int by = arrival.getBlockY();
        int bz = arrival.getBlockZ() + 3;
        r.setRegion(dw.getName(), bx - 1, by, bz, bx + 1, by + 2, bz);
        if (plugin.getConfig().getBoolean("portals.return-clear-space", true)) {
            for (int x = r.x1; x <= r.x2; x++) {
                for (int y = r.y1; y <= r.y2; y++) {
                    for (int z = r.z1; z <= r.z2; z++) {
                        Block b = dw.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) {
                            b.setType(Material.AIR, false);
                        }
                    }
                }
            }
        }
        r.destination = src.world;
        r.arrivalSpawn = false;
        World sw = Bukkit.getWorld(src.world);
        double ax = src.centerX();
        double az = src.z2 + 2.5;
        if (sw != null && !(isOpen(sw, (int) Math.floor(ax), src.y1, (int) Math.floor(az)))) {
            az = src.centerZ();
        }
        r.ax = ax;
        r.ay = src.y1;
        r.az = az;
        r.fillMode = src.fillMode;
        r.fillMaterial = src.fillMaterial;
        r.protectFill = src.protectFill;
        r.particles = src.particles;
        r.particleType = src.particleType;
        r.sound = src.sound;
        r.cooldown = src.cooldown;
        r.trigger = src.trigger;
        r.returnPortal = src.id;
        src.returnPortal = rid;
        add(r);
        refill(r);
        return r;
    }

    private boolean isOpen(World w, int x, int y, int z) {
        return w.getBlockAt(x, y, z).isPassable() && w.getBlockAt(x, y + 1, z).isPassable();
    }

    public void shutdown() {
        // finaliza trabajos de relleno pendientes de golpe
        int guard = 0;
        while (!jobs.isEmpty() && guard++ < 1000) {
            tickFill();
        }
        saveSync();
    }

    public VaultHook vault() {
        return vault;
    }

    public void forgetPlayer(UUID id) {
        graceUntil.remove(id);
        cooldowns.remove(id);
    }
}
