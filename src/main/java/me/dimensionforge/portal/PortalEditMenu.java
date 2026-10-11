package me.dimensionforge.portal;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.gui.ConfirmMenu;
import me.dimensionforge.gui.Menu;
import me.dimensionforge.gui.SettingsMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/** Crear y editar portales. */
public final class PortalEditMenu extends SettingsMenu {

    private final Portal portal;
    private final boolean creating;
    private String draftId = "";
    private boolean autoReturn = false;
    private String lastFillSig;

    public PortalEditMenu(DimensionForgePlugin plugin, Player viewer, Menu parent, Portal portal, boolean creating) {
        super(plugin, viewer, parent, creating ? "<green>Crear portal" : "<light_purple>Editar portal: " + portal.id);
        this.portal = portal;
        this.creating = creating;
        this.lastFillSig = fillSig();
    }

    private String fillSig() {
        return portal.fillMode + "|" + portal.fillMaterial + "|" + portal.x1 + portal.y1 + portal.z1 + portal.x2 + portal.y2 + portal.z2;
    }

    @Override
    protected void changed() {
        if (creating) {
            return;
        }
        String sig = fillSig();
        if (!sig.equals(lastFillSig)) {
            lastFillSig = sig;
            plugin.portals().rebuildIndex();
            if (!plugin.portals().refill(portal) && portal.fillMode != Portal.FillMode.NONE) {
                plugin.messages().send(viewer, "portal.fill-failed");
            }
        }
        plugin.portals().save();
    }

    @Override
    protected void define() {
        info("Región", Material.MAP, "Mundo: " + portal.world,
                portal.x1 + "," + portal.y1 + "," + portal.z1 + " → " + portal.x2 + "," + portal.y2 + "," + portal.z2,
                "Volumen: " + portal.volume());
        if (creating) {
            str("ID / nombre del portal", Material.NAME_TAG, () -> draftId, v -> draftId = v.toLowerCase().trim(),
                    "a-z, 0-9, _ y -");
        }
        action("Dimensión destino: " + (portal.destination.isEmpty() ? "(elegir)" : portal.destination), Material.ENDER_EYE,
                c -> new DimensionPicker(plugin, viewer, this, name -> {
                    portal.destination = name;
                    changed();
                    open();
                }).open(), "Click para elegir");
        bool("Llegada: spawn de la dimensión", Material.RED_BED, () -> portal.arrivalSpawn, v -> portal.arrivalSpawn = v,
                "Desactivado = usa la posición guardada");
        action("Llegada: usar mi posición", Material.COMPASS, c -> {
            Location l = viewer.getLocation();
            portal.arrivalSpawn = false;
            portal.ax = l.getX();
            portal.ay = l.getY();
            portal.az = l.getZ();
            portal.ayaw = l.getYaw();
            portal.apitch = l.getPitch();
            if (portal.destination.isEmpty()) {
                portal.destination = l.getWorld().getName();
            }
            changed();
            refresh();
        }, String.format("Guardada: %.1f %.1f %.1f (yaw %.0f)", portal.ax, portal.ay, portal.az, portal.ayaw));
        if (creating) {
            bool("Crear portal de regreso automático", Material.RECOVERY_COMPASS, () -> autoReturn, v -> autoReturn = v,
                    "Crea un portal en el destino que vuelve aquí");
        } else {
            str("Portal de regreso (id)", Material.RECOVERY_COMPASS, () -> portal.returnPortal, v -> portal.returnPortal = v.toLowerCase(),
                    "Definir a mano; vacío = ninguno");
        }
        cycle("Relleno visual", Material.END_PORTAL_FRAME, () -> portal.fillMode, v -> portal.fillMode = v,
                Portal.FillMode.values(), "NONE = vacío");
        str("Bloque de relleno (modo BLOCK)", Material.STONE, () -> portal.fillMaterial, v -> portal.fillMaterial = v.toUpperCase());
        bool("Proteger el relleno", Material.SHIELD, () -> portal.protectFill, v -> portal.protectFill = v);
        bool("Partículas", Material.BLAZE_POWDER, () -> portal.particles, v -> portal.particles = v);
        str("Tipo de partícula", Material.FIREWORK_STAR, () -> portal.particleType, v -> portal.particleType = v.toUpperCase());
        str("Sonido", Material.NOTE_BLOCK, () -> portal.sound, v -> portal.sound = v, "Clave, ej: entity.enderman.teleport");
        intVal("Cooldown (segundos)", Material.CLOCK, () -> portal.cooldown, v -> portal.cooldown = v, 1, 0, 3600);
        str("Permiso (opcional)", Material.TRIPWIRE_HOOK, () -> portal.permission, v -> portal.permission = v);
        str("Costo: ítem (material)", Material.GOLD_INGOT, () -> portal.costMaterial, v -> portal.costMaterial = v.toUpperCase());
        intVal("Costo: cantidad de ítems", Material.GOLD_NUGGET, () -> portal.costAmount, v -> portal.costAmount = v, 1, 0, 2304);
        dbl("Costo: dinero (Vault)", Material.EMERALD, () -> portal.costMoney, v -> portal.costMoney = v, 10, 0, 1.0E9,
                vaultNote());
        str("Título al entrar", Material.NAME_TAG, () -> portal.title, v -> portal.title = v, "Título|Subtítulo");
        str("Mensaje al entrar", Material.OAK_SIGN, () -> portal.message, v -> portal.message = v, "MiniMessage");
        cycle("Activación", Material.OAK_PRESSURE_PLATE, () -> portal.trigger, v -> portal.trigger = v,
                Portal.Trigger.values(), "WALK = caminar dentro, CLICK = click derecho");

        if (creating) {
            action("CREAR PORTAL", Material.LIME_CONCRETE, c -> create(), "Usa la región seleccionada");
        } else {
            action("Mover región con el hacha", Material.GOLDEN_AXE, c -> moveRegion(), "Usa tu selección actual");
            action("Teletransportarme al portal", Material.ENDER_PEARL, c -> {
                World w = Bukkit.getWorld(portal.world);
                if (w == null) {
                    plugin.messages().send(viewer, "portal.world-not-loaded", "world", portal.world);
                    return;
                }
                viewer.closeInventory();
                viewer.teleportAsync(new Location(w, portal.centerX(), portal.y1, portal.z1 - 1.5));
            });
            action("Borrar portal", Material.TNT, c -> new ConfirmMenu(plugin, viewer, "<red>¿Borrar " + portal.id + "?",
                    List.of("<gray>También se limpia su relleno."), () -> {
                plugin.portals().remove(portal);
                plugin.messages().send(viewer, "portal.deleted", "id", portal.id);
                new PortalListMenu(plugin, viewer, null).open();
            }, this::open).open());
        }
    }

    private String vaultNote() {
        return plugin.portals().vault().available() ? "Vault detectado" : "Sin Vault: se ignora";
    }

    private void moveRegion() {
        PortalWand.Selection s = plugin.wand().get(viewer);
        if (s == null || !s.complete()) {
            plugin.messages().send(viewer, "portal.need-selection");
            return;
        }
        portal.setRegion(s.world, s.p1[0], s.p1[1], s.p1[2], s.p2[0], s.p2[1], s.p2[2]);
        changed();
        plugin.messages().send(viewer, "portal.region-moved", "id", portal.id);
        refresh();
    }

    private void create() {
        String err = plugin.portals().validateId(draftId);
        if (err != null) {
            plugin.messages().send(viewer, err, "id", draftId);
            return;
        }
        if (portal.destination.isEmpty() || (plugin.dimensions().get(portal.destination) == null
                && Bukkit.getWorld(portal.destination) == null)) {
            plugin.messages().send(viewer, "portal.destination-unavailable", "destination", portal.destination);
            return;
        }
        long max = plugin.getConfig().getLong("portals.max-fill-volume", 4096);
        if (portal.fillMode != Portal.FillMode.NONE && portal.volume() > max) {
            plugin.messages().send(viewer, "portal.too-big", "max", max);
            return;
        }
        Portal real = new Portal(draftId);
        real.copyFrom(portal);
        plugin.portals().add(real);
        plugin.portals().refill(real);
        if (autoReturn) {
            Portal r = plugin.portals().createReturn(real);
            if (r != null) {
                plugin.messages().send(viewer, "portal.return-created", "id", r.id);
            } else {
                plugin.messages().send(viewer, "portal.return-failed");
            }
        }
        plugin.wand().clear(viewer);
        plugin.messages().send(viewer, "portal.created", "id", real.id);
        new PortalEditMenu(plugin, viewer, null, real, false).open();
    }
}
