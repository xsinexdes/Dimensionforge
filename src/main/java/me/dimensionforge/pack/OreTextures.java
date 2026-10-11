package me.dimensionforge.pack;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Menas a partir de PNG sueltos: se meten en pack/textures/ore/ y el plugin crea solo el modelo de cubo.
 * <ul>
 *   <li><b>mithril.png</b> → cubo con la misma textura en las 6 caras.</li>
 *   <li><b>mithril_side.png</b> (+ opcional _top y _bottom) → cubo con cara lateral, superior e inferior distintas.</li>
 *   <li><b>mithril.png.mcmeta</b> junto al PNG → textura animada (se copia tal cual).</li>
 * </ul>
 * Sin dependencias de Bukkit ni de ImageIO: el tamaño del PNG se lee de su cabecera.
 */
public final class OreTextures {

    /** Una mena detectada: id y archivos de textura por cara (null = no existe). */
    public record Entry(String id, File all, File top, File side, File bottom) {
        public boolean sided() {
            return side != null;
        }
    }

    /** Resultado del escaneo. */
    public static final class Scan {
        public final List<Entry> entries = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    private static final byte[] PNG_SIG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private OreTextures() {
    }

    /** Ids de menas que hay en la carpeta (sin validar a fondo). */
    public static List<String> ids(File dir) {
        Scan s = scan(dir);
        List<String> out = new ArrayList<>();
        for (Entry e : s.entries) {
            out.add(e.id());
        }
        return out;
    }

    public static Scan scan(File dir) {
        Scan scan = new Scan();
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".png"));
        if (files == null || files.length == 0) {
            return scan;
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        // nombre en minúsculas (sin .png) -> archivo
        Map<String, File> byName = new TreeMap<>();
        for (File f : files) {
            String base = f.getName().substring(0, f.getName().length() - 4);
            String name = base.toLowerCase(Locale.ROOT);
            if (!name.matches("[a-z0-9_]+")) {
                scan.errors.add("textures/ore/" + f.getName() + ": el nombre solo puede tener letras sin acentos, números y _ (se ignora).");
                continue;
            }
            if (!name.equals(base)) {
                scan.warnings.add("textures/ore/" + f.getName() + ": Minecraft exige minúsculas; se usa '" + name + "'.");
            }
            byName.put(name, f);
        }
        Map<String, Entry> out = new LinkedHashMap<>();
        // 1) menas con caras distintas: existe X_side
        for (String name : byName.keySet()) {
            if (!name.endsWith("_side") || name.length() <= 5) {
                continue;
            }
            String id = name.substring(0, name.length() - 5);
            File all = byName.get(id);
            File side = byName.get(name);
            File top = byName.getOrDefault(id + "_top", all);
            File bottom = byName.getOrDefault(id + "_bottom", top);
            if (top == null) {
                top = side;
                scan.warnings.add("textures/ore/" + id + ": hay _side pero no _top ni " + id + ".png; la cara de arriba usa la lateral.");
                bottom = byName.getOrDefault(id + "_bottom", side);
            }
            out.put(id, new Entry(id, all, top, side, bottom));
        }
        // 2) menas de una sola textura (las que no son parte de un grupo con _side)
        for (Map.Entry<String, File> e : byName.entrySet()) {
            String name = e.getKey();
            if (out.containsKey(name)) {
                continue;
            }
            String stem = stripFaceSuffix(name);
            if (stem != null && out.containsKey(stem)) {
                continue; // es la cara _top/_bottom/_side de una mena ya registrada
            }
            out.put(name, new Entry(name, e.getValue(), null, null, null));
        }
        for (Entry e : out.values()) {
            if (check(e, scan)) {
                scan.entries.add(e);
            }
        }
        return scan;
    }

    private static String stripFaceSuffix(String name) {
        for (String suf : new String[]{"_side", "_top", "_bottom"}) {
            if (name.endsWith(suf) && name.length() > suf.length()) {
                return name.substring(0, name.length() - suf.length());
            }
        }
        return null;
    }

    /** Valida los PNG de una mena. Devuelve false si no se puede usar. */
    private static boolean check(Entry e, Scan scan) {
        boolean ok = true;
        for (File f : files(e)) {
            int[] wh = size(f);
            String shown = "textures/ore/" + f.getName();
            if (wh == null) {
                scan.errors.add(shown + ": no es un PNG válido (¿lo renombraste desde otro formato? guárdalo como PNG de verdad).");
                ok = false;
                continue;
            }
            File meta = new File(f.getParentFile(), f.getName() + ".mcmeta");
            boolean animated = meta.isFile();
            if (wh[0] != wh[1] && !(animated && wh[1] % wh[0] == 0)) {
                scan.errors.add(shown + ": mide " + wh[0] + "x" + wh[1] + " y debe ser cuadrada (16x16, 32x32...). "
                        + "Solo se admite una tira vertical si hay un archivo " + f.getName() + ".mcmeta (animación).");
                ok = false;
            } else if (wh[0] > 512) {
                scan.warnings.add(shown + ": mide " + wh[0] + "px; texturas tan grandes hacen pesado el pack.");
            }
        }
        return ok;
    }

    /** Archivos distintos de una mena. */
    public static List<File> files(Entry e) {
        List<File> l = new ArrayList<>();
        for (File f : new File[]{e.all(), e.top(), e.side(), e.bottom()}) {
            if (f != null && !l.contains(f)) {
                l.add(f);
            }
        }
        return l;
    }

    /** Nombre de textura dentro del pack (sin namespace ni .png), p. ej. "ore/mithril_side". */
    public static String texName(File f) {
        String n = f.getName();
        return "ore/" + n.substring(0, n.length() - 4).toLowerCase(Locale.ROOT);
    }

    /** JSON del modelo: un cubo de Minecraft con las texturas de la mena (los nombres solo llevan a-z, 0-9 y _). */
    public static String modelJson(Entry e) {
        if (e.sided()) {
            return "{\"parent\":\"minecraft:block/cube_bottom_top\",\"textures\":{"
                    + "\"top\":\"dimensionforge:" + texName(e.top()) + "\","
                    + "\"bottom\":\"dimensionforge:" + texName(e.bottom()) + "\","
                    + "\"side\":\"dimensionforge:" + texName(e.side()) + "\"}}";
        }
        return "{\"parent\":\"minecraft:block/cube_all\",\"textures\":{\"all\":\"dimensionforge:" + texName(e.all()) + "\"}}";
    }

    /** Añade al mapa del pack los archivos PNG (y .mcmeta) de la mena. */
    public static void addFiles(Entry e, Map<String, byte[]> files) throws IOException {
        for (File f : files(e)) {
            String name = texName(f);
            files.put("assets/dimensionforge/textures/" + name + ".png", Files.readAllBytes(f.toPath()));
            File meta = new File(f.getParentFile(), f.getName() + ".mcmeta");
            if (meta.isFile()) {
                files.put("assets/dimensionforge/textures/" + name + ".png.mcmeta", Files.readAllBytes(meta.toPath()));
            }
        }
    }

    /** Ancho y alto del PNG leídos de su cabecera IHDR, o null si no es un PNG. */
    static int[] size(File f) {
        try (InputStream in = Files.newInputStream(f.toPath())) {
            byte[] h = in.readNBytes(24);
            if (h.length < 24) {
                return null;
            }
            for (int i = 0; i < PNG_SIG.length; i++) {
                if (h[i] != PNG_SIG[i]) {
                    return null;
                }
            }
            if (h[12] != 'I' || h[13] != 'H' || h[14] != 'D' || h[15] != 'R') {
                return null;
            }
            int w = ((h[16] & 0xFF) << 24) | ((h[17] & 0xFF) << 16) | ((h[18] & 0xFF) << 8) | (h[19] & 0xFF);
            int hh = ((h[20] & 0xFF) << 24) | ((h[21] & 0xFF) << 16) | ((h[22] & 0xFF) << 8) | (h[23] & 0xFF);
            if (w <= 0 || hh <= 0) {
                return null;
            }
            return new int[]{w, hh};
        } catch (IOException ex) {
            return null;
        }
    }
}
