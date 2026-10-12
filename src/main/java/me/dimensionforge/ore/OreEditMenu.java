package me.dimensionforge.ore;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Edición completa de una mena custom. */
public final class OreEditMenu extends SettingsMenu {

    private enum Tier { WOOD, STONE, IRON, GOLD, DIAMOND, NETHERITE }

    private final OreDef o;

    public OreEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, OreDef o) {
        super(plugin, viewer, parent, "<gold>Mena: " + o.id);
        this.o = o;
    }

    @Override
    protected void changed() {
        plugin.ores().changed();
        plugin.ores().refreshDisplays(o);
    }

    private Tier tier() {
        try {
            return Tier.valueOf(o.minTier.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return Tier.WOOD;
        }
    }

    private static String join(List<String> l) {
        return String.join(",", l);
    }

    private static List<String> split(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split(",")) {
            if (!p.isBlank()) {
                out.add(p.trim());
            }
        }
        return out;
    }

    @Override
    protected void define() {
        info("Mena", o.base().isItem() ? o.base() : Material.STONE, "Id: " + o.id, "Mundos: " + o.worldsText(), "Base: " + o.baseMaterial);
        bool("Activada", Material.LEVER, () -> o.enabled, v -> o.enabled = v);

        // apariencia
        cycle("Apariencia", Material.PAINTING, () -> o.appearance, v -> o.appearance = v, OreDef.Appearance.values(),
                "BLOCK = solo bloque, MODEL = modelo encima");
        action("Textura", Material.ITEM_FRAME, c -> {
            if (c.shift()) {
                plugin.textInput().request(viewer, "Modelo de BetterModel o id de textura", o.model, text -> {
                    setModel(text.trim());
                    changed();
                    open();
                }, this::open);
            } else {
                new TexturePickMenu(plugin, viewer, this, o, this::changed).open();
            }
        }, "Actual: " + (o.appearance == OreDef.Appearance.MODEL ? o.modelId() : "ninguna (bloque base)"),
                "Click: elegir de la lista (salen solas las de pack/textures/ore/)",
                "Shift+click: escribir un id a mano (BetterModel)");

        action("Mundos donde aparece", Material.GRASS_BLOCK, c -> new WorldPickMenu(plugin, viewer, this, o).open(),
                "Actual: " + o.worldsText(), "Click: elegir uno o varios mundos");

        // herramienta
        cycle("Herramienta: modo", Material.IRON_PICKAXE, () -> o.toolMode, v -> o.toolMode = v, OreDef.ToolMode.values(),
                "TIER = tier mínimo, SPECIFIC = ítem concreto");
        cycle("Herramienta: tier mínimo", Material.STONE_PICKAXE, this::tier, v -> o.minTier = v.name(), Tier.values());
        bool("Solo picos", Material.WOODEN_PICKAXE, () -> o.pickaxeOnly, v -> o.pickaxeOnly = v, "Desactivado = cualquier herramienta");
        str("Herramienta específica: material", Material.NETHERITE_PICKAXE, () -> o.toolMaterial, v -> o.toolMaterial = v.toUpperCase());
        intVal("Herramienta específica: CustomModelData", Material.PAINTING, () -> o.toolCmd, v -> o.toolCmd = v, 1, 0, Integer.MAX_VALUE);
        str("Herramienta específica: nombre", Material.NAME_TAG, () -> o.toolName, v -> o.toolName = v);
        bool("Con herramienta incorrecta: se rompe", Material.BARRIER, () -> o.wrongToolBreaks, v -> o.wrongToolBreaks = v,
                "Desactivado = no se rompe");
        bool("Con herramienta incorrecta: suelta drops", Material.HOPPER, () -> o.wrongToolDrops, v -> o.wrongToolDrops = v);
        str("Sonido herramienta incorrecta", Material.NOTE_BLOCK, () -> o.wrongToolSound, v -> o.wrongToolSound = v);

        // dureza y durabilidad
        dbl("Dureza (segundos)", Material.OBSIDIAN, () -> o.hardness, v -> o.hardness = v, 0.5, 0.0, 3600,
                "Tiempo con la herramienta mínima");
        bool("Aplicar Eficiencia/Prisa/Fatiga/agua", Material.GOLDEN_PICKAXE, () -> o.applyModifiers, v -> o.applyModifiers = v);
        str("Sonido de golpe", Material.NOTE_BLOCK, () -> o.hitSound, v -> o.hitSound = v);
        intVal("Durabilidad: daño por rotura", Material.ANVIL, () -> o.durabilityCost, v -> o.durabilityCost = v, 1, 0, 1000);
        bool("No gasta durabilidad", Material.ENCHANTED_BOOK, () -> o.noDurability, v -> o.noDurability = v);

        // drops
        action("Drops (" + o.drops.size() + ")", Material.CHEST, c -> new DropsMenu(plugin, viewer, this, "<gold>Drops de " + o.id,
                o.drops, this::changed).open());
        bool("Fortuna", Material.LAPIS_LAZULI, () -> o.fortune, v -> o.fortune = v);
        bool("Toque de seda suelta el bloque", Material.STRING, () -> o.silkTouchDropsBlock, v -> o.silkTouchDropsBlock = v,
                "Desactivado = Toque de seda se ignora");
        bool("Drops directo al inventario", Material.ENDER_CHEST, () -> o.dropsToInventory, v -> o.dropsToInventory = v);
        intVal("Experiencia mínima", Material.EXPERIENCE_BOTTLE, () -> o.xpMin, v -> o.xpMin = v, 1, 0, 1000);
        intVal("Experiencia máxima", Material.EXPERIENCE_BOTTLE, () -> o.xpMax, v -> o.xpMax = v, 1, 0, 1000);
        str("Partícula al romper", Material.FIREWORK_STAR, () -> o.breakParticle, v -> o.breakParticle = v.toUpperCase(),
                "BLOCK usa el bloque base");
        intVal("Cantidad de partículas", Material.GLOWSTONE_DUST, () -> o.breakParticleCount, v -> o.breakParticleCount = v, 5, 1, 200);
        str("Sonido al romper", Material.NOTE_BLOCK, () -> o.breakSound, v -> o.breakSound = v);

        // respawn
        intVal("Respawn (segundos, 0 = no reaparece)", Material.CLOCK, () -> o.respawnSeconds, v -> o.respawnSeconds = v, 10, 0, 86400);
        str("Bloque agotado", Material.BEDROCK, () -> o.depletedMaterial, v -> {
            Material m = Material.matchMaterial(v.trim());
            if (m != null && m.isBlock() && !m.isAir()) {
                o.depletedMaterial = m.name();
            } else {
                plugin.messages().send(viewer, "error.invalid-material", "value", v);
            }
        });

        // generación
        bool("Generación natural", Material.GRASS_BLOCK, () -> o.generate, v -> o.generate = v);
        intVal("Tamaño de veta", Material.RAW_IRON, () -> o.veinSize, v -> o.veinSize = v, 1, 1, 64);
        dbl("Vetas por chunk", Material.COPPER_INGOT, () -> o.veinsPerChunk, v -> o.veinsPerChunk = v, 0.5, 0.0, 64);
        bool("Solo cerca del world border", Material.BARRIER, () -> o.borderOnly, v -> o.borderOnly = v,
                "Solo genera en una franja pegada al borde del mundo", "(ideal para mapas de WorldPainter)");
        intVal("Ancho de la franja del borde (bloques)", Material.CHAIN, () -> o.borderBand, v -> o.borderBand = v, 25, 1, 5000);
        intVal("Altura mínima (Y)", Material.LADDER, () -> o.yMin, v -> o.yMin = v, 8, -64, 320);
        intVal("Altura máxima (Y)", Material.LADDER, () -> o.yMax, v -> o.yMax = v, 8, -64, 320);
        str("Biomas (separados por coma)", Material.OAK_SAPLING, () -> join(o.biomes), v -> o.biomes = split(v), "Vacío = todos");
        str("Reemplaza (materiales, coma)", Material.STONE, () -> join(o.replace), v -> o.replace = split(v.toUpperCase()),
                "Vacío = piedra, deepslate, netherrack, end_stone...");

        // utilidades
        action("Escanear chunks cargados", Material.SPYGLASS, c -> {
            int[] r = plugin.ores().scan(o);
            plugin.messages().send(viewer, "ore.scan", "id", o.id, "total", r[0], "missing", r[1]);
        });
        action("Regenerar alrededor (radio 3 chunks)", Material.TNT, c -> regen(3), "Solo chunks cargados de esta dimensión");
        action("Borrar mena", Material.BARRIER, c -> new ConfirmMenu(plugin, viewer, "<red>¿Borrar " + o.id + "?", List.of(), () -> {
            plugin.ores().remove(o);
            plugin.messages().send(viewer, "ore.removed", "id", o.id);
            new OreListMenu(plugin, viewer, null, "").open();
        }, this::open).open());
    }

    private void setModel(String model) {
        if (!model.isEmpty() && !plugin.models().hasModel(model) && !plugin.packs().availableOreModels().contains(model)) {
            plugin.messages().send(viewer, "ore.model-unknown", "model", model);
        }
        o.model = model;
        if (!model.isEmpty() && o.appearance == OreDef.Appearance.BLOCK) {
            o.appearance = OreDef.Appearance.MODEL;
        }
    }

    private void regen(int radius) {
        World w = viewer.getWorld();
        if (!o.appearsIn(w.getName())) {
            plugin.messages().send(viewer, "ore.regen-wrong-world", "world", o.worldsText());
            return;
        }
        int total = 0;
        int cx = viewer.getLocation().getBlockX() >> 4;
        int cz = viewer.getLocation().getBlockZ() >> 4;
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int z = cz - radius; z <= cz + radius; z++) {
                if (w.isChunkLoaded(x, z)) {
                    Chunk c = w.getChunkAt(x, z);
                    total += plugin.ores().regenerate(c);
                }
            }
        }
        plugin.messages().send(viewer, "ore.regen", "count", total);
    }
}
