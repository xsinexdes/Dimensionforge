package me.dimensionforge.mob;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import me.dimensionforge.ore.DropsMenu;
import me.dimensionforge.util.Text;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

/** Edición completa de un mob custom. */
public final class MobEditMenu extends SettingsMenu {

    private final MobDef d;

    public MobEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, MobDef d) {
        super(plugin, viewer, parent, "<dark_gray>Mob: <white>" + d.id);
        this.d = d;
    }

    @Override
    protected void changed() {
        plugin.mobs().changed(d);
    }

    @Override
    protected void define() {
        bool("Activo", Material.LEVER, () -> d.enabled, v -> d.enabled = v, "Desactivado: no aparece de forma natural");
        str("Nombre visible", Material.NAME_TAG, () -> d.displayName, v -> d.displayName = v.isBlank() ? d.id : v, "MiniMessage");
        action("Tipo base: " + d.entityType, Material.ZOMBIE_HEAD, c -> plugin.textInput().request(viewer,
                "Tipo base (EntityType)", d.entityType, text -> {
                    try {
                        EntityType t = EntityType.valueOf(text.trim().toUpperCase());
                        if (MobManager.classOf(t) == null) {
                            throw new IllegalArgumentException();
                        }
                        d.entityType = t.name();
                        plugin.mobs().save();
                        plugin.messages().send(viewer, "mob.type-changed");
                    } catch (IllegalArgumentException ex) {
                        plugin.messages().send(viewer, "error.mob-type-invalid", "value", text);
                    }
                    open();
                }, this::open), "Solo afecta a los mobs que aparezcan a partir de ahora");

        // estadísticas
        dbl("Vida máxima", Material.RED_DYE, () -> d.health, v -> d.health = v, 5, 1, 4096, "Atributo max_health");
        dbl("Daño de ataque", Material.IRON_SWORD, () -> d.damage, v -> d.damage = v, 1, 0, 2048, "Atributo attack_damage (no todos los mobs lo tienen)");
        dbl("Velocidad", Material.SUGAR, () -> d.speed, v -> d.speed = v, 0.01, 0.01, 2, "Atributo movement_speed");
        dbl("Armadura", Material.IRON_CHESTPLATE, () -> d.armor, v -> d.armor = v, 1, 0, 30);
        dbl("Dureza de armadura", Material.NETHERITE_CHESTPLATE, () -> d.toughness, v -> d.toughness = v, 1, 0, 20);
        dbl("Resistencia al empuje", Material.ANVIL, () -> d.knockbackResistance, v -> d.knockbackResistance = v, 0.1, 0, 1);
        dbl("Rango de seguimiento", Material.SPYGLASS, () -> d.followRange, v -> d.followRange = v, 4, 1, 128);
        dbl("Escala real (hitbox)", Material.PUFFERFISH, () -> d.scale, v -> d.scale = v, 0.1, 0.0625, 16, "Atributo scale: cambia también la caja de colisión");
        dbl("Regeneración (vida/s)", Material.GLISTERING_MELON_SLICE, () -> d.regenPerSecond, v -> d.regenPerSecond = v, 0.5, 0, 100);

        // apariencia
        bool("Nombre siempre visible", Material.OAK_SIGN, () -> d.nameVisible, v -> d.nameVisible = v);
        bool("Resplandor", Material.GLOWSTONE_DUST, () -> d.glowing, v -> d.glowing = v);
        bool("Silenciar sonidos vanilla", Material.NOTE_BLOCK, () -> d.silentVanilla, v -> d.silentVanilla = v, "Útil si usas tus propios sonidos");
        bool("Inmune al fuego", Material.MAGMA_CREAM, () -> d.fireImmune, v -> d.fireImmune = v);

        // modelo
        action("Modelo: " + (d.hasModel() ? Text.escape(d.model) : "ninguno"), Material.ARMOR_STAND,
                c -> new MobModelMenu(plugin, viewer, this, d).open(),
                plugin.models().available() ? "Modelos de BetterModel (" + plugin.models().modelNames().size() + ")"
                        : "BetterModel no está instalado: modo simple");
        dbl("Escala del modelo", Material.PUFFERFISH_BUCKET, () -> d.modelScale, v -> d.modelScale = v, 0.1, 0.05, 20);
        action("Animaciones por estado", Material.JUKEBOX, c -> new MobAnimationsMenu(plugin, viewer, this, d).open(),
                "Idle, caminar, atacar, daño, muerte y aparición");

        // drops
        action("Drops (" + d.drops.size() + ")", Material.CHEST, c -> new DropsMenu(plugin, viewer, this,
                "<dark_gray>Drops de " + d.id, d.drops, () -> plugin.mobs().save()).open());
        bool("Reemplazar drops vanilla", Material.HOPPER, () -> d.replaceVanillaDrops, v -> d.replaceVanillaDrops = v);
        intVal("XP mínima", Material.EXPERIENCE_BOTTLE, () -> d.xpMin, v -> d.xpMin = v, 1, 0, 10000);
        intVal("XP máxima", Material.EXPERIENCE_BOTTLE, () -> d.xpMax, v -> d.xpMax = v, 1, 0, 10000, "0 = sin XP propia");

        // sonidos y partículas
        str("Sonido ambiente", Material.JUKEBOX, () -> d.ambientSound, v -> d.ambientSound = v.trim(), "Clave, ej. entity.zombie.ambient o paquete:sonido");
        intVal("Intervalo del ambiente (s)", Material.CLOCK, () -> d.ambientIntervalSeconds, v -> d.ambientIntervalSeconds = v, 1, 1, 600);
        str("Sonido al recibir daño", Material.NOTE_BLOCK, () -> d.hurtSound, v -> d.hurtSound = v.trim());
        str("Sonido al morir", Material.NOTE_BLOCK, () -> d.deathSound, v -> d.deathSound = v.trim());
        str("Partícula continua", Material.BLAZE_POWDER, () -> d.particle, v -> d.particle = v.trim(), "Nombre de Particle, ej. FLAME");
        intVal("Cantidad de partículas", Material.BLAZE_POWDER, () -> d.particleCount, v -> d.particleCount = v, 1, 0, 50);

        // barra de jefe
        bool("Barra de jefe", Material.DRAGON_HEAD, () -> d.bossBar, v -> d.bossBar = v);
        str("Título de la barra", Material.OAK_SIGN, () -> d.bossBarTitle, v -> d.bossBarTitle = v, "Vacío = nombre del mob");
        str("Color de la barra", Material.PINK_DYE, () -> d.bossBarColor, v -> d.bossBarColor = v.trim().toUpperCase(), "PINK BLUE RED GREEN YELLOW PURPLE WHITE");
        str("Estilo de la barra", Material.LADDER, () -> d.bossBarStyle, v -> d.bossBarStyle = v.trim().toUpperCase(), "PROGRESS NOTCHED_6 NOTCHED_10 NOTCHED_12 NOTCHED_20");
        dbl("Rango de la barra", Material.SPYGLASS, () -> d.bossBarRange, v -> d.bossBarRange = v, 4, 4, 256);

        // habilidades, fases, aparición
        bool("Habilidades activas", Material.BLAZE_POWDER, () -> d.skillsEnabled, v -> d.skillsEnabled = v);
        action("Habilidades (" + d.skills.size() + ")", Material.ENCHANTED_BOOK,
                c -> new MobSkillsMenu(plugin, viewer, this, d, d.skills, "<dark_gray>Habilidades de " + d.id).open());
        action("Fases de jefe (" + d.phases.size() + ")", Material.WITHER_SKELETON_SKULL,
                c -> new BossPhasesMenu(plugin, viewer, this, d).open(), "Cambios por porcentaje de vida");
        action("Reglas de aparición (" + d.spawnRules.size() + ")", Material.SPAWNER,
                c -> new SpawnRulesMenu(plugin, viewer, this, d).open());
        action("Aparecer aquí", Material.EGG, c -> {
            if (plugin.mobs().spawn(d, viewer.getLocation()) != null) {
                plugin.messages().send(viewer, "mob.spawned", "id", d.id);
            } else {
                plugin.messages().send(viewer, "mob.spawn-failed", "id", d.id);
            }
        });
        action("Aplicar a mobs vivos", Material.ANVIL, c -> {
            plugin.mobs().refresh(d);
            plugin.messages().send(viewer, "mob.refreshed", "id", d.id);
        }, "Reaplica estadísticas y modelo (se hace solo al editar)");
        info("Id: " + d.id, Material.BOOK, "Dimensión: " + (d.dimension.isEmpty() ? "ninguna" : d.dimension));
    }
}
