package me.dimensionforge.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte un .bbmodel (Blockbench) a un modelo de ítem de Java Edition.
 * Soporta cubos con rotación en un eje, UV por cara y texturas incrustadas en base64.
 * Las mallas (mesh) y las rotaciones en varios ejes no existen en modelos de Java y se avisan.
 */
public final class BbmodelConverter {

    public static final class Result {
        public String json;
        /** ruta relativa dentro del pack (assets/dimensionforge/textures/...png) -> bytes */
        public final Map<String, byte[]> textures = new LinkedHashMap<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();

        public boolean ok() {
            return errors.isEmpty() && json != null;
        }
    }

    private static final String[] FACES = {"north", "east", "south", "west", "up", "down"};

    private BbmodelConverter() {
    }

    public static Result convert(String source, String type, String id) {
        Result r = new Result();
        JsonObject root;
        try {
            root = JsonParser.parseString(source).getAsJsonObject();
        } catch (Exception ex) {
            r.errors.add(type + "/" + id + ": JSON inválido (" + ex.getMessage() + ")");
            return r;
        }
        double resW = 16;
        double resH = 16;
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject res = root.getAsJsonObject("resolution");
            resW = num(res.get("width"), 16);
            resH = num(res.get("height"), 16);
        }
        String prefix = type + "/" + id;

        // texturas
        JsonArray texArr = root.has("textures") && root.get("textures").isJsonArray() ? root.getAsJsonArray("textures") : new JsonArray();
        JsonObject texturesOut = new JsonObject();
        for (int i = 0; i < texArr.size(); i++) {
            JsonObject t = texArr.get(i).getAsJsonObject();
            String src = t.has("source") && !t.get("source").isJsonNull() ? t.get("source").getAsString() : "";
            int comma = src.indexOf(',');
            if (!src.startsWith("data:image/png") || comma < 0) {
                r.errors.add(prefix + ": la textura #" + i + " no está incrustada como PNG (en Blockbench: Texture → guarda 'Embed' o usa File → Save with embedded textures).");
                continue;
            }
            try {
                byte[] png = Base64.getDecoder().decode(src.substring(comma + 1));
                String name = prefix + "_" + i;
                r.textures.put("assets/dimensionforge/textures/" + name + ".png", png);
                texturesOut.addProperty(String.valueOf(i), "dimensionforge:" + name);
                if (i == 0) {
                    texturesOut.addProperty("particle", "dimensionforge:" + name);
                }
            } catch (IllegalArgumentException ex) {
                r.errors.add(prefix + ": la textura #" + i + " tiene base64 corrupto.");
            }
        }
        if (texturesOut.size() == 0 && r.errors.isEmpty()) {
            r.errors.add(prefix + ": el modelo no tiene texturas.");
        }

        // elementos
        JsonArray elementsOut = new JsonArray();
        JsonArray elems = root.has("elements") && root.get("elements").isJsonArray() ? root.getAsJsonArray("elements") : new JsonArray();
        for (JsonElement el : elems) {
            JsonObject e = el.getAsJsonObject();
            String kind = e.has("type") ? e.get("type").getAsString() : "cube";
            String name = e.has("name") ? e.get("name").getAsString() : "?";
            if (!kind.equals("cube")) {
                r.warnings.add(prefix + ": el elemento '" + name + "' es de tipo '" + kind + "' y se ignora (solo hay cubos en Java).");
                continue;
            }
            double[] from = arr(e.get("from"), 3);
            double[] to = arr(e.get("to"), 3);
            double inflate = num(e.get("inflate"), 0);
            for (int k = 0; k < 3; k++) {
                from[k] -= inflate;
                to[k] += inflate;
            }
            for (int k = 0; k < 3; k++) {
                if (from[k] < -16 || to[k] > 32) {
                    r.warnings.add(prefix + ": '" + name + "' se sale del rango -16..32 de los modelos de Java; el cliente lo recorta.");
                    break;
                }
            }
            JsonObject out = new JsonObject();
            out.add("from", vec(from));
            out.add("to", vec(to));
            double[] rot = e.has("rotation") ? arr(e.get("rotation"), 3) : new double[3];
            double[] origin = e.has("origin") ? arr(e.get("origin"), 3) : new double[]{8, 8, 8};
            int axisIdx = -1;
            int nonZero = 0;
            for (int k = 0; k < 3; k++) {
                if (Math.abs(rot[k]) > 1.0E-4) {
                    nonZero++;
                    if (axisIdx < 0 || Math.abs(rot[k]) > Math.abs(rot[axisIdx])) {
                        axisIdx = k;
                    }
                }
            }
            if (nonZero > 0) {
                if (nonZero > 1) {
                    r.warnings.add(prefix + ": '" + name + "' rota en varios ejes; Java solo admite uno, se usa el mayor.");
                }
                double angle = rot[axisIdx];
                double snapped = Math.round(angle / 22.5) * 22.5;
                if (Math.abs(snapped) > 45 || Math.abs(angle - snapped) > 0.01) {
                    r.warnings.add(prefix + ": '" + name + "' rota " + angle + "°; Java solo admite -45, -22.5, 0, 22.5 y 45. Se ajusta a "
                            + Math.max(-45, Math.min(45, snapped)) + "°.");
                    angle = Math.max(-45, Math.min(45, snapped));
                }
                JsonObject ro = new JsonObject();
                ro.add("origin", vec(origin));
                ro.addProperty("axis", new String[]{"x", "y", "z"}[axisIdx]);
                ro.addProperty("angle", angle);
                out.add("rotation", ro);
            }
            JsonObject faces = new JsonObject();
            JsonObject bbFaces = e.has("faces") && e.get("faces").isJsonObject() ? e.getAsJsonObject("faces") : new JsonObject();
            for (String face : FACES) {
                if (!bbFaces.has(face) || !bbFaces.get(face).isJsonObject()) {
                    continue;
                }
                JsonObject f = bbFaces.getAsJsonObject(face);
                if (!f.has("texture") || f.get("texture").isJsonNull()) {
                    continue; // cara sin textura = invisible
                }
                int tex = (int) num(f.get("texture"), 0);
                if (!texturesOut.has(String.valueOf(tex))) {
                    r.warnings.add(prefix + ": '" + name + "' usa la textura #" + tex + " que no existe; cara omitida.");
                    continue;
                }
                double[] uv = f.has("uv") ? arr(f.get("uv"), 4) : new double[]{0, 0, 16, 16};
                JsonObject fo = new JsonObject();
                JsonArray uvOut = new JsonArray();
                uvOut.add(round(uv[0] * 16.0 / resW));
                uvOut.add(round(uv[1] * 16.0 / resH));
                uvOut.add(round(uv[2] * 16.0 / resW));
                uvOut.add(round(uv[3] * 16.0 / resH));
                fo.add("uv", uvOut);
                fo.addProperty("texture", "#" + tex);
                int frot = (int) num(f.get("rotation"), 0);
                if (frot != 0) {
                    fo.addProperty("rotation", frot);
                }
                faces.add(face, fo);
            }
            out.add("faces", faces);
            elementsOut.add(out);
        }
        if (elementsOut.size() == 0) {
            r.errors.add(prefix + ": no tiene ningún cubo convertible.");
        }
        JsonObject model = new JsonObject();
        model.addProperty("credit", "Generado por DimensionForge desde " + prefix + ".bbmodel");
        model.add("textures", texturesOut);
        model.add("elements", elementsOut);
        JsonObject display = new JsonObject();
        JsonObject gui = new JsonObject();
        gui.addProperty("rotation", 0);
        display.add("gui", gui);
        model.add("display", display);
        r.json = model.toString();
        return r;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static double num(JsonElement e, double def) {
        try {
            return e == null || e.isJsonNull() ? def : e.getAsDouble();
        } catch (Exception ex) {
            return def;
        }
    }

    private static double[] arr(JsonElement e, int n) {
        double[] out = new double[n];
        if (e != null && e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < n && i < a.size(); i++) {
                out[i] = num(a.get(i), 0);
            }
        }
        return out;
    }

    private static JsonArray vec(double[] v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            a.add(round(d));
        }
        return a;
    }
}
