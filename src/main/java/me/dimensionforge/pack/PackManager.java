package me.dimensionforge.pack;

import me.dimensionforge.DimensionForgePlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generador automático del resource pack: lee pack/models/&lt;tipo&gt;/&lt;id&gt;.bbmodel, los convierte,
 * asigna CMDs estables, crea el zip con SHA-1 y lo sirve/envía a los jugadores.
 */
public final class PackManager {

    /** UUID fijos: el cliente reemplaza el pack anterior en vez de acumularlos. */
    private static final UUID OWN_ID = UUID.nameUUIDFromBytes("dimensionforge:pack".getBytes(StandardCharsets.UTF_8));
    private static final UUID BM_ID = UUID.nameUUIDFromBytes("dimensionforge:bettermodel".getBytes(StandardCharsets.UTF_8));

    private final DimensionForgePlugin plugin;
    private final CmdRegistry cmds;
    private final PackServer server;
    private final File root;
    private final AtomicBoolean building = new AtomicBoolean();

    private volatile byte[] zip = new byte[0];
    private volatile byte[] sha1 = new byte[0];
    private volatile String sha1Hex = "";
    private volatile List<String> warnings = List.of();
    private volatile List<String> errors = List.of();
    private volatile int modelCount;
    private volatile long builtAt;

    public PackManager(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        this.cmds = new CmdRegistry(plugin);
        this.server = new PackServer(plugin.getLogger());
        this.root = new File(plugin.getDataFolder(), "pack");
    }

    public CmdRegistry cmds() {
        return cmds;
    }

    public File modelsDir() {
        return new File(root, "models");
    }

    /** Carpeta donde se sueltan los PNG de las menas (pack/textures/ore). */
    public File oreTexturesDir() {
        return new File(root, "textures/ore");
    }

    /** Ids de menas disponibles: PNG de la carpeta de texturas + .bbmodel de models/ore, ordenados. */
    public List<String> availableOreModels() {
        java.util.TreeSet<String> set = new java.util.TreeSet<>(OreTextures.ids(oreTexturesDir()));
        File[] bb = new File(modelsDir(), "ore").listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
        if (bb != null) {
            for (File f : bb) {
                set.add(f.getName().substring(0, f.getName().length() - ".bbmodel".length()).toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(set);
    }

    public List<String> warnings() {
        return warnings;
    }

    public List<String> errors() {
        return errors;
    }

    public String hash() {
        return sha1Hex;
    }

    public int modelCount() {
        return modelCount;
    }

    public boolean hasPack() {
        return zip.length > 0;
    }

    public boolean building() {
        return building.get();
    }

    public long builtAt() {
        return builtAt;
    }

    // ---------------------------------------------------------------- ciclo de vida

    public void start() {
        cmds.load();
        new File(root, "models/ore").mkdirs();
        oreTexturesDir().mkdirs();
        new File(root, "models/item").mkdirs();
        new File(root, "extra").mkdirs();
        if (!plugin.getConfig().getBoolean("pack.enabled", true)) {
            return;
        }
        if (plugin.getConfig().getBoolean("pack.build-on-start", true)) {
            build(null, null);
        }
        startServerIfNeeded();
    }

    public void shutdown() {
        server.stop();
    }

    private String hostMode() {
        return plugin.getConfig().getString("pack.host.mode", "INTERNAL").toUpperCase(Locale.ROOT);
    }

    private void startServerIfNeeded() {
        if (!hostMode().equals("INTERNAL")) {
            return;
        }
        try {
            server.start(plugin.getConfig().getString("pack.host.bind", "0.0.0.0"),
                    plugin.getConfig().getInt("pack.host.port", 8765));
            refreshExtra();
        } catch (IOException ex) {
            plugin.getLogger().warning("No se pudo iniciar el servidor del pack (¿puerto ocupado?): " + ex.getMessage()
                    + ". Cambia pack.host.port o usa pack.host.mode: EXTERNAL.");
        }
    }

    private void refreshExtra() {
        if (plugin.getConfig().getString("pack.bettermodel.mode", "NONE").equalsIgnoreCase("SEPARATE")) {
            File f = new File(plugin.getConfig().getString("pack.bettermodel.file", "plugins/BetterModel/build/build.zip"));
            server.setExtra(f.toPath());
        } else {
            server.setExtra(null);
        }
    }

    // ---------------------------------------------------------------- construcción

    /** Reconstruye el pack en segundo plano. Al terminar ejecuta done en el hilo principal. */
    public void build(CommandSender who, Runnable done) {
        if (!building.compareAndSet(false, true)) {
            if (who != null) {
                plugin.messages().send(who, "pack.already-building");
            }
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                doBuild();
            } catch (Exception ex) {
                errors = List.of("Error inesperado al construir el pack: " + ex);
                plugin.getLogger().warning("Error construyendo el pack: " + ex);
            } finally {
                building.set(false);
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                server.setPack(zip);
                refreshExtra();
                cmds.save();
                if (plugin.ores() != null) {
                    plugin.ores().syncTextures();
                }
                if (who != null) {
                    plugin.messages().send(who, errors.isEmpty() ? "pack.built" : "pack.built-with-errors",
                            "models", modelCount, "warnings", warnings.size(), "errors", errors.size(), "hash", shortHash());
                }
                if (done != null) {
                    done.run();
                }
            });
        });
    }

    public String shortHash() {
        return sha1Hex.length() > 8 ? sha1Hex.substring(0, 8) : sha1Hex;
    }

    private void doBuild() throws IOException {
        List<String> warn = new ArrayList<>();
        List<String> err = new ArrayList<>();
        Map<String, byte[]> files = new TreeMap<>();

        int pf = plugin.getConfig().getInt("pack.pack-format", 34);
        files.put("pack.mcmeta", ("{\"pack\":{\"pack_format\":" + pf + ",\"description\":\"DimensionForge\"}}")
                .getBytes(StandardCharsets.UTF_8));

        int converted = 0;
        File modelsDir = modelsDir();
        File[] types = modelsDir.listFiles(File::isDirectory);
        if (types != null) {
            for (File typeDir : types) {
                String type = typeDir.getName().toLowerCase(Locale.ROOT);
                if (!type.matches("[a-z0-9_]+")) {
                    warn.add("Carpeta ignorada (nombre inválido): " + typeDir.getName());
                    continue;
                }
                File[] list = typeDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".bbmodel"));
                if (list == null) {
                    continue;
                }
                sortFiles(list);
                for (File f : list) {
                    String base = f.getName().substring(0, f.getName().length() - ".bbmodel".length());
                    String id = base.toLowerCase(Locale.ROOT);
                    if (!id.matches("[a-z0-9_]+")) {
                        err.add(type + "/" + base + ": el nombre solo puede tener a-z, 0-9 y _ (se ignora).");
                        continue;
                    }
                    if (!id.equals(base)) {
                        warn.add(type + "/" + base + ": se usa el id en minúsculas '" + id + "'.");
                    }
                    String src = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                    BbmodelConverter.Result r = BbmodelConverter.convert(src, type, id);
                    warn.addAll(r.warnings);
                    err.addAll(r.errors);
                    if (!r.ok()) {
                        continue;
                    }
                    cmds.assign(type, id);
                    files.put("assets/dimensionforge/models/" + type + "/" + id + ".json", r.json.getBytes(StandardCharsets.UTF_8));
                    files.putAll(r.textures);
                    converted++;
                }
            }
        }

        // Menas con PNG suelto: pack/textures/ore/<id>.png → modelo de cubo automático (sin bbmodel)
        OreTextures.Scan texScan = OreTextures.scan(oreTexturesDir());
        warn.addAll(texScan.warnings);
        err.addAll(texScan.errors);
        for (OreTextures.Entry te : texScan.entries) {
            String mkey = "assets/dimensionforge/models/ore/" + te.id() + ".json";
            if (files.containsKey(mkey)) {
                warn.add("ore/" + te.id() + ": existe como .bbmodel y como PNG; gana el .bbmodel.");
                continue;
            }
            cmds.assign("ore", te.id());
            files.put(mkey, OreTextures.modelJson(te).getBytes(StandardCharsets.UTF_8));
            OreTextures.addFiles(te, files);
            converted++;
        }

        // Ítems base con las asignaciones de CMD (se incluyen también modelos antiguos que sigan existiendo)
        Map<String, Map<String, Integer>> snap = cmds.snapshot();
        TreeMap<Integer, String> entries = new TreeMap<>();
        for (Map.Entry<String, Map<String, Integer>> te : snap.entrySet()) {
            for (Map.Entry<String, Integer> me : te.getValue().entrySet()) {
                if (files.containsKey("assets/dimensionforge/models/" + te.getKey() + "/" + me.getKey() + ".json")) {
                    entries.put(me.getValue(), "dimensionforge:" + te.getKey() + "/" + me.getKey());
                }
            }
        }
        String base = cmds.baseMaterial().name().toLowerCase(Locale.ROOT);
        String mode = plugin.getConfig().getString("pack.format-mode", "legacy").toLowerCase(Locale.ROOT);
        if (!entries.isEmpty()) {
            if (mode.equals("items")) {
                files.put("assets/minecraft/items/" + base + ".json", itemsDefinition(base, entries).getBytes(StandardCharsets.UTF_8));
            } else {
                files.put("assets/minecraft/models/item/" + base + ".json", legacyOverrides(base, entries).getBytes(StandardCharsets.UTF_8));
            }
        }
        if (mode.equals("items") && pf < 46) {
            warn.add("pack.format-mode=items requiere pack-format 46 o superior (Minecraft 1.21.4+); con 1.21.1 usa 'legacy'.");
        }
        if (mode.equals("legacy") && pf >= 46) {
            warn.add("pack.format-mode=legacy con pack-format >= 46: desde 1.21.4 los clientes ignoran los overrides de models/item; usa 'items'.");
        }

        // Archivos extra del administrador (se copian tal cual, sin sobrescribir lo generado)
        Path extra = new File(root, "extra").toPath();
        if (Files.isDirectory(extra)) {
            try (Stream<Path> walk = Files.walk(extra)) {
                for (Path p : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String rel = extra.relativize(p).toString().replace('\\', '/');
                    if (files.containsKey(rel)) {
                        warn.add("extra/" + rel + " choca con un archivo generado y se ignora.");
                    } else {
                        files.put(rel, Files.readAllBytes(p));
                    }
                }
            }
        }

        // Validación final: todo modelo propio debe encontrar sus texturas dentro del zip
        // (si falta alguna, el cliente pinta el cuadro morado y negro).
        for (Map.Entry<String, byte[]> me : new ArrayList<>(files.entrySet())) {
            String k = me.getKey();
            if (!k.startsWith("assets/dimensionforge/models/") || !k.endsWith(".json")) {
                continue;
            }
            try {
                com.google.gson.JsonObject mo = com.google.gson.JsonParser
                        .parseString(new String(me.getValue(), StandardCharsets.UTF_8)).getAsJsonObject();
                if (mo.has("textures") && mo.get("textures").isJsonObject()) {
                    for (Map.Entry<String, com.google.gson.JsonElement> te : mo.getAsJsonObject("textures").entrySet()) {
                        String ref = te.getValue().getAsString();
                        if (ref.startsWith("#")) {
                            continue;
                        }
                        String ns = ref.contains(":") ? ref.substring(0, ref.indexOf(':')) : "minecraft";
                        String path = ref.contains(":") ? ref.substring(ref.indexOf(':') + 1) : ref;
                        if (ns.equals("dimensionforge") && !path.startsWith("block/") && !path.startsWith("item/")) {
                            err.add(k.substring("assets/dimensionforge/models/".length()) + ": la textura '" + ref
                                    + "' debe estar en textures/block/ o textures/item/ (Minecraft no carga otras carpetas).");
                        }
                        if (ns.equals("dimensionforge") && !files.containsKey("assets/dimensionforge/textures/" + path + ".png")) {
                            err.add(k.substring("assets/dimensionforge/models/".length()) + ": la textura '" + ref + "' no está en el pack (saldría morada).");
                        }
                    }
                }
            } catch (Exception ex) {
                err.add(k + ": JSON de modelo ilegible (" + ex.getMessage() + ")");
            }
        }

        if (converted == 0 && files.size() <= 1) {
            warn.add("No hay texturas en pack/textures/ore/ ni .bbmodel en pack/models/<tipo>/. El pack solo contiene pack.mcmeta.");
        }

        byte[] data = zip(files);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(data);
            sha1 = digest;
            sha1Hex = HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
        File out = new File(root, "build");
        out.mkdirs();
        Files.write(new File(out, "DimensionForge.zip").toPath(), data);
        Files.writeString(new File(out, "DimensionForge.zip.sha1").toPath(), sha1Hex, StandardCharsets.UTF_8);
        zip = data;
        modelCount = converted;
        warnings = Collections.unmodifiableList(warn);
        errors = Collections.unmodifiableList(err);
        builtAt = System.currentTimeMillis();
        plugin.getLogger().info("Pack generado: " + converted + " modelos, " + warn.size() + " avisos, " + err.size()
                + " errores, SHA-1 " + sha1Hex);
    }

    private static void sortFiles(File[] files) {
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
    }

    private static String legacyOverrides(String base, TreeMap<Integer, String> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"minecraft:item/").append(base)
                .append("\"},\"overrides\":[");
        boolean first = true;
        for (Map.Entry<Integer, String> e : entries.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"predicate\":{\"custom_model_data\":").append(e.getKey()).append("},\"model\":\"")
                    .append(e.getValue()).append("\"}");
        }
        return sb.append("]}").toString();
    }

    private static String itemsDefinition(String base, TreeMap<Integer, String> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":{\"type\":\"minecraft:range_dispatch\",\"property\":\"minecraft:custom_model_data\",\"index\":0,")
                .append("\"fallback\":{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/").append(base)
                .append("\"},\"entries\":[");
        boolean first = true;
        for (Map.Entry<Integer, String> e : entries.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"threshold\":").append(e.getKey()).append(",\"model\":{\"type\":\"minecraft:model\",\"model\":\"")
                    .append(e.getValue()).append("\"}}");
        }
        return sb.append("]}}").toString();
    }

    /** Zip determinista (orden alfabético y fecha fija): el mismo contenido da siempre el mismo SHA-1. */
    private static byte[] zip(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry ze = new ZipEntry(e.getKey());
                ze.setTimeLocal(LocalDateTime.of(2020, 1, 1, 0, 0));
                zos.putNextEntry(ze);
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    // ---------------------------------------------------------------- envío

    /** URL del pack propio, o null si no hay forma de servirlo. */
    public String url() {
        String v = shortHash();
        switch (hostMode()) {
            case "EXTERNAL" -> {
                String u = plugin.getConfig().getString("pack.host.external-url", "");
                return u.isBlank() ? null : u + (u.contains("?") ? "&" : "?") + "v=" + v;
            }
            case "INTERNAL" -> {
                String host = plugin.getConfig().getString("pack.host.public-address", "");
                if (host.isBlank()) {
                    return null;
                }
                return "http://" + host + ":" + plugin.getConfig().getInt("pack.host.port", 8765) + "/pack.zip?v=" + v;
            }
            default -> {
                return null;
            }
        }
    }

    private String bettermodelUrl() {
        if (!plugin.getConfig().getString("pack.bettermodel.mode", "NONE").equalsIgnoreCase("SEPARATE")) {
            return null;
        }
        String host = plugin.getConfig().getString("pack.host.public-address", "");
        if (host.isBlank() || !hostMode().equals("INTERNAL")) {
            return null;
        }
        return "http://" + host + ":" + plugin.getConfig().getInt("pack.host.port", 8765) + "/bettermodel.zip";
    }

    /** Envía el pack a un jugador. Devuelve false si no hay pack o URL. */
    public boolean send(Player p) {
        String url = url();
        if (!hasPack() || url == null || !plugin.getConfig().getBoolean("pack.enabled", true)) {
            return false;
        }
        boolean force = plugin.getConfig().getBoolean("pack.force", false);
        // En Paper 1.21.1 addResourcePack recibe el aviso como String (formato clásico con §)
        String prompt = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                .serialize(me.dimensionforge.util.Text.mm(plugin.messages().raw("pack.prompt")));
        p.addResourcePack(OWN_ID, url, sha1, prompt, force);
        String bm = bettermodelUrl();
        if (bm != null) {
            byte[] bmHash = bettermodelHash();
            if (bmHash != null) {
                p.addResourcePack(BM_ID, bm, bmHash, prompt, force);
            }
        }
        return true;
    }

    private byte[] bettermodelHash() {
        try {
            Path f = new File(plugin.getConfig().getString("pack.bettermodel.file", "plugins/BetterModel/build/build.zip")).toPath();
            if (!Files.isRegularFile(f)) {
                return null;
            }
            return MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(f));
        } catch (Exception ex) {
            return null;
        }
    }

    public int sendAll() {
        int n = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (send(p)) {
                n++;
            }
        }
        return n;
    }

    /** Textos de validación para mostrar a un administrador. */
    public List<String> report() {
        List<String> out = new ArrayList<>();
        for (String e : errors) {
            out.add("<red>✖ " + me.dimensionforge.util.Text.escape(e));
        }
        for (String w : warnings) {
            out.add("<yellow>⚠ " + me.dimensionforge.util.Text.escape(w));
        }
        if (out.isEmpty()) {
            out.add("<green>Sin avisos ni errores.");
        }
        return out;
    }
}
