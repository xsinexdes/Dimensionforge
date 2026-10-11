package me.dimensionforge.ore;

import me.dimensionforge.dimension.DimensionData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Definición de una mena custom (ores.yml). */
public final class OreDef {

    public enum Appearance { BLOCK, MODEL }

    public enum ToolMode { TIER, SPECIFIC }

    public final String id;
    public String dimension = "";
    public String baseMaterial = "STONE";
    public boolean enabled = true;

    // apariencia
    public Appearance appearance = Appearance.BLOCK;
    public String model = "";

    // herramienta
    public ToolMode toolMode = ToolMode.TIER;
    public String minTier = "WOOD";
    public boolean pickaxeOnly = true;
    public String toolMaterial = "";
    public int toolCmd = 0;
    public String toolName = "";
    public boolean wrongToolBreaks = false;
    public boolean wrongToolDrops = false;
    public String wrongToolSound = "block.note_block.bass";

    // dureza
    public double hardness = 3.0;
    public boolean applyModifiers = true;
    public String hitSound = "";

    // durabilidad
    public int durabilityCost = 1;
    public boolean noDurability = false;

    // drops
    public List<DropEntry> drops = new ArrayList<>();
    public boolean fortune = true;
    public boolean silkTouchDropsBlock = false;
    public boolean dropsToInventory = false;
    public int xpMin = 0;
    public int xpMax = 0;

    // efectos al romper
    public String breakParticle = "BLOCK";
    public int breakParticleCount = 20;
    public String breakSound = "block.stone.break";

    // reaparición
    public int respawnSeconds = 60;
    public String depletedMaterial = "STONE";

    // generación
    public boolean generate = true;
    public int veinSize = 6;
    public double veinsPerChunk = 4.0;
    public int yMin = -64;
    public int yMax = 64;
    public List<String> biomes = new ArrayList<>();
    public List<String> replace = new ArrayList<>();

    public OreDef(String id) {
        this.id = id;
    }

    /** Modelo efectivo: el elegido, o (si no hay) el PNG que se llame igual que la mena. */
    public String modelId() {
        return model == null || model.isBlank() ? id : model;
    }

    public Material base() {
        Material m = Material.matchMaterial(baseMaterial == null ? "" : baseMaterial);
        return m == null || !m.isBlock() ? Material.STONE : m;
    }

    public Material depleted() {
        Material m = Material.matchMaterial(depletedMaterial == null ? "" : depletedMaterial);
        return m == null || !m.isBlock() ? Material.STONE : m;
    }

    public void save(ConfigurationSection s) {
        s.set("dimension", dimension);
        s.set("base", baseMaterial);
        s.set("enabled", enabled);
        s.set("appearance", appearance.name());
        s.set("model", model);
        s.set("tool.mode", toolMode.name());
        s.set("tool.min-tier", minTier);
        s.set("tool.pickaxe-only", pickaxeOnly);
        s.set("tool.material", toolMaterial);
        s.set("tool.cmd", toolCmd);
        s.set("tool.name", toolName);
        s.set("tool.wrong-breaks", wrongToolBreaks);
        s.set("tool.wrong-drops", wrongToolDrops);
        s.set("tool.wrong-sound", wrongToolSound);
        s.set("hardness", hardness);
        s.set("apply-modifiers", applyModifiers);
        s.set("hit-sound", hitSound);
        s.set("durability.cost", durabilityCost);
        s.set("durability.none", noDurability);
        DropEntry.saveList(s, "drops", drops);
        s.set("fortune", fortune);
        s.set("silk-touch-drops-block", silkTouchDropsBlock);
        s.set("drops-to-inventory", dropsToInventory);
        s.set("xp.min", xpMin);
        s.set("xp.max", xpMax);
        s.set("effects.particle", breakParticle);
        s.set("effects.particle-count", breakParticleCount);
        s.set("effects.sound", breakSound);
        s.set("respawn.seconds", respawnSeconds);
        s.set("respawn.depleted", depletedMaterial);
        s.set("generation.enabled", generate);
        s.set("generation.vein-size", veinSize);
        s.set("generation.veins-per-chunk", veinsPerChunk);
        s.set("generation.y-min", yMin);
        s.set("generation.y-max", yMax);
        s.set("generation.biomes", biomes);
        s.set("generation.replace", replace);
    }

    public static OreDef load(String id, ConfigurationSection s) {
        OreDef o = new OreDef(id);
        o.dimension = s.getString("dimension", "");
        o.baseMaterial = s.getString("base", "STONE");
        o.enabled = s.getBoolean("enabled", true);
        o.appearance = DimensionData.enumOf(Appearance.class, s.getString("appearance"), Appearance.BLOCK);
        o.model = s.getString("model", "");
        o.toolMode = DimensionData.enumOf(ToolMode.class, s.getString("tool.mode"), ToolMode.TIER);
        o.minTier = s.getString("tool.min-tier", "WOOD");
        o.pickaxeOnly = s.getBoolean("tool.pickaxe-only", true);
        o.toolMaterial = s.getString("tool.material", "");
        o.toolCmd = s.getInt("tool.cmd", 0);
        o.toolName = s.getString("tool.name", "");
        o.wrongToolBreaks = s.getBoolean("tool.wrong-breaks", false);
        o.wrongToolDrops = s.getBoolean("tool.wrong-drops", false);
        o.wrongToolSound = s.getString("tool.wrong-sound", "block.note_block.bass");
        o.hardness = s.getDouble("hardness", 3.0);
        o.applyModifiers = s.getBoolean("apply-modifiers", true);
        o.hitSound = s.getString("hit-sound", "");
        o.durabilityCost = s.getInt("durability.cost", 1);
        o.noDurability = s.getBoolean("durability.none", false);
        o.drops = DropEntry.loadList(s, "drops");
        o.fortune = s.getBoolean("fortune", true);
        o.silkTouchDropsBlock = s.getBoolean("silk-touch-drops-block", false);
        o.dropsToInventory = s.getBoolean("drops-to-inventory", false);
        o.xpMin = s.getInt("xp.min", 0);
        o.xpMax = s.getInt("xp.max", 0);
        o.breakParticle = s.getString("effects.particle", "BLOCK");
        o.breakParticleCount = s.getInt("effects.particle-count", 20);
        o.breakSound = s.getString("effects.sound", "block.stone.break");
        o.respawnSeconds = s.getInt("respawn.seconds", 60);
        o.depletedMaterial = s.getString("respawn.depleted", "STONE");
        o.generate = s.getBoolean("generation.enabled", true);
        o.veinSize = s.getInt("generation.vein-size", 6);
        o.veinsPerChunk = s.getDouble("generation.veins-per-chunk", 4.0);
        o.yMin = s.getInt("generation.y-min", -64);
        o.yMax = s.getInt("generation.y-max", 64);
        o.biomes = new ArrayList<>(s.getStringList("generation.biomes"));
        o.replace = new ArrayList<>(s.getStringList("generation.replace"));
        return o;
    }
}
