package me.dimensionforge.cmd;

import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.dimension.CreateDimensionMenu;
import me.dimensionforge.dimension.DimensionData;
import me.dimensionforge.dimension.MainMenu;
import me.dimensionforge.mob.MobDef;
import me.dimensionforge.mob.MobListMenu;
import me.dimensionforge.mob.MobManager;
import me.dimensionforge.ore.DropEntry;
import me.dimensionforge.ore.OreDef;
import me.dimensionforge.ore.OreListMenu;
import me.dimensionforge.pack.PackMenu;
import me.dimensionforge.portal.Portal;
import me.dimensionforge.portal.PortalListMenu;
import me.dimensionforge.skill.SkillLibraryMenu;
import me.dimensionforge.skill.model.SkillBinding;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** /dimension (alias /dim, /dforge): menús y subcomandos con autocompletado. */
public final class DimensionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of("menu", "list", "tp", "create", "reload", "wand", "portal", "ore",
            "mob", "skill", "pack", "help");
    private static final List<String> TIERS = List.of("WOOD", "STONE", "IRON", "GOLD", "DIAMOND", "NETHERITE");

    private final DimensionForgePlugin plugin;

    public DimensionCommand(DimensionForgePlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- ejecución

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            if (!(s instanceof Player p)) {
                msg(s, "cmd.player-only");
                return true;
            }
            if (!admin(s)) {
                return true;
            }
            new MainMenu(plugin, p).open();
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        String[] r = Arrays.copyOfRange(a, 1, a.length);
        switch (sub) {
            case "menu" -> {
                Player p = player(s);
                if (p != null && admin(s)) {
                    new MainMenu(plugin, p).open();
                }
            }
            case "list" -> {
                if (admin(s)) {
                    listDimensions(s);
                }
            }
            case "tp" -> teleport(s, r);
            case "create" -> {
                Player p = player(s);
                if (p != null && admin(s)) {
                    new CreateDimensionMenu(plugin, p, null).open();
                }
            }
            case "reload" -> {
                if (admin(s)) {
                    plugin.reloadAll();
                    msg(s, "cmd.reloaded");
                }
            }
            case "wand" -> {
                Player p = player(s);
                if (p != null && admin(s)) {
                    p.getInventory().addItem(plugin.wand().create());
                    msg(s, "cmd.wand-given");
                }
            }
            case "portal" -> {
                if (admin(s)) {
                    portal(s, r);
                }
            }
            case "ore" -> {
                if (admin(s)) {
                    ore(s, r);
                }
            }
            case "mob" -> {
                if (admin(s)) {
                    mob(s, r);
                }
            }
            case "skill" -> {
                if (admin(s)) {
                    skill(s, r);
                }
            }
            case "pack" -> {
                if (admin(s)) {
                    pack(s, r);
                }
            }
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        for (String line : plugin.messages().rawList("cmd.help")) {
            s.sendMessage(Text.mm(line));
        }
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("dimensionforge.admin")) {
            return true;
        }
        msg(s, "cmd.no-permission");
        return false;
    }

    private Player player(CommandSender s) {
        if (s instanceof Player p) {
            return p;
        }
        msg(s, "cmd.player-only");
        return null;
    }

    private void msg(CommandSender s, String key, Object... pairs) {
        plugin.messages().send(s, key, pairs);
    }

    // ---------------------------------------------------------------- dimensiones

    private void listDimensions(CommandSender s) {
        msg(s, "cmd.dim-list-header", "count", plugin.dimensions().all().size());
        for (DimensionData d : plugin.dimensions().all()) {
            msg(s, "cmd.dim-list-entry", "name", d.name, "env", d.environment.name(),
                    "loaded", plugin.dimensions().isLoaded(d.name) ? "sí" : "no");
        }
    }

    private void teleport(CommandSender s, String[] r) {
        if (r.length < 1) {
            msg(s, "cmd.usage", "usage", "/dimension tp <dimensión> [jugador]");
            return;
        }
        DimensionData d = plugin.dimensions().get(r[0]);
        if (d == null) {
            msg(s, "cmd.dim-unknown", "name", r[0]);
            return;
        }
        Player target;
        if (r.length >= 2) {
            if (!admin(s)) {
                return;
            }
            target = Bukkit.getPlayerExact(r[1]);
            if (target == null) {
                msg(s, "cmd.player-unknown", "name", r[1]);
                return;
            }
        } else {
            target = player(s);
            if (target == null) {
                return;
            }
            if (!s.hasPermission("dimensionforge.tp") && !s.hasPermission("dimensionforge.admin")) {
                msg(s, "cmd.no-permission");
                return;
            }
        }
        plugin.dimensions().teleport(target, d);
    }

    // ---------------------------------------------------------------- portales

    private void portal(CommandSender s, String[] r) {
        String sub = r.length == 0 ? "menu" : r[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu", "create" -> {
                Player p = player(s);
                if (p == null) {
                    return;
                }
                if (sub.equals("menu")) {
                    new PortalListMenu(plugin, p, null).open();
                } else {
                    PortalListMenu.startCreate(plugin, p, null);
                }
            }
            case "list" -> {
                msg(s, "cmd.portal-list-header", "count", plugin.portals().all().size());
                for (Portal p : plugin.portals().all()) {
                    msg(s, "cmd.portal-list-entry", "id", p.id, "world", p.world, "dest", p.destination.isEmpty() ? "-" : p.destination);
                }
            }
            case "tp" -> {
                Player p = player(s);
                Portal portal = r.length > 1 ? plugin.portals().get(r[1]) : null;
                if (p == null) {
                    return;
                }
                if (portal == null) {
                    msg(s, "cmd.portal-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                World w = Bukkit.getWorld(portal.world);
                if (w == null) {
                    msg(s, "cmd.dim-not-loaded", "name", portal.world);
                    return;
                }
                p.teleportAsync(new Location(w, (portal.x1 + portal.x2) / 2.0 + 0.5, portal.y1, (portal.z1 + portal.z2) / 2.0 + 0.5));
            }
            case "delete" -> {
                Portal portal = r.length > 1 ? plugin.portals().get(r[1]) : null;
                if (portal == null) {
                    msg(s, "cmd.portal-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                plugin.portals().remove(portal);
                msg(s, "cmd.portal-deleted", "id", portal.id);
            }
            default -> msg(s, "cmd.usage", "usage", "/dimension portal <menu|create|list|tp|delete>");
        }
    }

    // ---------------------------------------------------------------- menas

    private void ore(CommandSender s, String[] r) {
        String sub = r.length == 0 ? "" : r[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                Player p = player(s);
                if (p != null) {
                    new OreListMenu(plugin, p, null, r.length > 1 ? r[1] : "").open();
                }
            }
            case "list" -> {
                List<OreDef> list = r.length > 1 ? plugin.ores().forDimension(r[1]) : new ArrayList<>(plugin.ores().all());
                msg(s, "cmd.ore-list-header", "count", list.size());
                for (OreDef o : list) {
                    msg(s, "cmd.ore-list-entry", "id", o.id, "dim", o.worldsText(), "base", o.baseMaterial,
                            "state", o.enabled ? "activa" : "desactivada");
                }
            }
            case "create" -> {
                if (r.length < 4) {
                    msg(s, "cmd.usage", "usage", "/dimension ore create <id> <dimensión> <material-base>");
                    return;
                }
                String id = r[1].toLowerCase(Locale.ROOT);
                Material m = Material.matchMaterial(r[3]);
                if (!id.matches("[a-z0-9_-]{1,32}") || plugin.ores().get(id) != null) {
                    msg(s, "error.ore-id-invalid", "id", r[1]);
                } else if (m == null || !m.isBlock() || m.isAir()) {
                    msg(s, "error.invalid-material", "value", r[3]);
                } else {
                    plugin.ores().create(r[2], id, m);
                    msg(s, "ore.created", "id", id);
                }
            }
            case "set" -> oreSet(s, r);
            case "info", "remove", "enable", "disable", "scan" -> {
                OreDef o = r.length > 1 ? plugin.ores().get(r[1]) : null;
                if (o == null) {
                    msg(s, "cmd.ore-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                switch (sub) {
                    case "info" -> {
                        msg(s, "cmd.ore-info", "id", o.id, "dim", o.worldsText(), "base", o.baseMaterial,
                                "hardness", o.hardness, "drops", o.drops.size(), "model", o.model.isEmpty() ? "-" : o.model,
                                "vein", o.veinSize, "perchunk", o.veinsPerChunk);
                    }
                    case "remove" -> {
                        plugin.ores().remove(o);
                        msg(s, "ore.removed", "id", o.id);
                    }
                    case "enable", "disable" -> {
                        o.enabled = sub.equals("enable");
                        plugin.ores().changed();
                        msg(s, "cmd.ore-toggled", "id", o.id, "state", o.enabled ? "activada" : "desactivada");
                    }
                    default -> {
                        int[] res = plugin.ores().scan(o);
                        msg(s, "cmd.ore-scan", "id", o.id, "total", res[0], "missing", res[1]);
                    }
                }
            }
            case "place" -> {
                Player p = player(s);
                if (p == null) {
                    return;
                }
                OreDef o = r.length > 1 ? plugin.ores().get(r[1]) : null;
                if (o == null) {
                    msg(s, "cmd.ore-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                // junto a la cara del bloque que miras (hasta 6 bloques); si no miras ninguno, 3 bloques delante
                org.bukkit.util.RayTraceResult hit = p.rayTraceBlocks(6);
                org.bukkit.block.Block target;
                if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                    target = hit.getHitBlock().getRelative(hit.getHitBlockFace());
                } else {
                    target = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(3)).getBlock();
                }
                plugin.ores().placeAt(target, o);
                msg(s, "cmd.ore-placed", "id", o.id);
            }
            case "regen" -> {
                Player p = player(s);
                if (p != null) {
                    int n = plugin.ores().regenerate(p.getLocation().getChunk());
                    msg(s, "cmd.ore-regen", "count", n);
                }
            }
            default -> msg(s, "cmd.usage", "usage", "/dimension ore <menu|list|create|set|info|remove|enable|disable|scan|place|regen>");
        }
    }

    private void oreSet(CommandSender s, String[] r) {
        if (r.length < 4) {
            msg(s, "cmd.usage", "usage", "/dimension ore set <id> <drop|model|tool|hardness|vein> ...");
            return;
        }
        OreDef o = plugin.ores().get(r[1]);
        if (o == null) {
            msg(s, "cmd.ore-unknown", "id", r[1]);
            return;
        }
        String what = r[2].toLowerCase(Locale.ROOT);
        try {
            switch (what) {
                case "drop" -> {
                    if (r[3].equalsIgnoreCase("clear")) {
                        o.drops.clear();
                    } else if (r[3].equalsIgnoreCase("hand")) {
                        Player p = player(s);
                        if (p == null) {
                            return;
                        }
                        ItemStackCheck.requireItem(p);
                        o.drops.add(DropEntry.fromItem(p.getInventory().getItemInMainHand()));
                    } else {
                        Material m = Material.matchMaterial(r[3]);
                        if (m == null || !m.isItem()) {
                            msg(s, "error.invalid-material", "value", r[3]);
                            return;
                        }
                        DropEntry d = new DropEntry();
                        d.material = m.name();
                        d.min = r.length > 4 ? Integer.parseInt(r[4]) : 1;
                        d.max = r.length > 5 ? Integer.parseInt(r[5]) : d.min;
                        d.chance = r.length > 6 ? Double.parseDouble(r[6]) : 100.0;
                        o.drops.add(d);
                    }
                }
                case "model" -> {
                    if (r[3].equalsIgnoreCase("none")) {
                        o.appearance = OreDef.Appearance.BLOCK;
                        o.model = "";
                    } else {
                        o.appearance = OreDef.Appearance.MODEL;
                        o.model = r[3];
                    }
                }
                case "tool" -> {
                    if (r[3].equalsIgnoreCase("tier") && r.length > 4) {
                        String t = r[4].toUpperCase(Locale.ROOT);
                        if (!TIERS.contains(t)) {
                            msg(s, "cmd.bad-value", "value", r[4]);
                            return;
                        }
                        o.toolMode = OreDef.ToolMode.TIER;
                        o.minTier = t;
                    } else if (r[3].equalsIgnoreCase("specific") && r.length > 4) {
                        Material m = Material.matchMaterial(r[4]);
                        if (m == null) {
                            msg(s, "error.invalid-material", "value", r[4]);
                            return;
                        }
                        o.toolMode = OreDef.ToolMode.SPECIFIC;
                        o.toolMaterial = m.name();
                        o.toolCmd = r.length > 5 ? Integer.parseInt(r[5]) : 0;
                    } else {
                        msg(s, "cmd.usage", "usage", "/dimension ore set <id> tool <tier <TIER>|specific <MATERIAL> [cmd]>");
                        return;
                    }
                }
                case "hardness" -> o.hardness = Math.max(0.05, Double.parseDouble(r[3].replace(',', '.')));
                case "vein" -> {
                    o.veinSize = Math.max(1, Integer.parseInt(r[3]));
                    if (r.length > 4) o.veinsPerChunk = Math.max(0, Double.parseDouble(r[4]));
                    if (r.length > 5) o.yMin = Integer.parseInt(r[5]);
                    if (r.length > 6) o.yMax = Integer.parseInt(r[6]);
                }
                default -> {
                    msg(s, "cmd.usage", "usage", "/dimension ore set <id> <drop|model|tool|hardness|vein> ...");
                    return;
                }
            }
        } catch (NumberFormatException | ItemStackCheck.NoItem ex) {
            msg(s, "cmd.bad-value", "value", String.valueOf(ex.getMessage()));
            return;
        }
        plugin.ores().changed();
        plugin.ores().refreshDisplays(o);
        msg(s, "cmd.ore-updated", "id", o.id);
    }

    // ---------------------------------------------------------------- mobs

    private void mob(CommandSender s, String[] r) {
        String sub = r.length == 0 ? "" : r[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                Player p = player(s);
                if (p != null) {
                    new MobListMenu(plugin, p, null, r.length > 1 ? r[1] : p.getWorld().getName()).open();
                }
            }
            case "list" -> {
                msg(s, "cmd.mob-list-header", "count", plugin.mobs().all().size());
                for (MobDef d : plugin.mobs().all()) {
                    msg(s, "cmd.mob-list-entry", "id", d.id, "type", d.entityType, "dim", d.dimension.isEmpty() ? "-" : d.dimension,
                            "model", d.hasModel() ? d.model : "simple");
                }
            }
            case "create" -> {
                if (r.length < 3) {
                    msg(s, "cmd.usage", "usage", "/dimension mob create <id> <TIPO> [dimensión]");
                    return;
                }
                String id = r[1].toLowerCase(Locale.ROOT);
                EntityType t;
                try {
                    t = EntityType.valueOf(r[2].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    t = null;
                }
                if (!id.matches("[a-z0-9_-]{1,32}") || plugin.mobs().get(id) != null) {
                    msg(s, "error.mob-id-invalid", "id", r[1]);
                } else if (t == null || MobManager.classOf(t) == null) {
                    msg(s, "error.mob-type-invalid", "value", r[2]);
                } else {
                    plugin.mobs().create(r.length > 3 ? r[3] : "", id, t);
                    msg(s, "mob.created", "id", id);
                }
            }
            case "spawn" -> {
                Player p = player(s);
                MobDef d = r.length > 1 ? plugin.mobs().get(r[1]) : null;
                if (p == null) {
                    return;
                }
                if (d == null) {
                    msg(s, "cmd.mob-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                int n = 1;
                try {
                    n = r.length > 2 ? Math.max(1, Math.min(20, Integer.parseInt(r[2]))) : 1;
                } catch (NumberFormatException ignored) {
                    // se queda en 1
                }
                for (int i = 0; i < n; i++) {
                    var e = plugin.mobs().spawn(d, p.getLocation());
                    if (e != null) {
                        plugin.mobs().fillHealth(e);
                    }
                }
                msg(s, "mob.spawned", "id", d.id);
            }
            case "remove", "info", "refresh" -> {
                MobDef d = r.length > 1 ? plugin.mobs().get(r[1]) : null;
                if (d == null) {
                    msg(s, "cmd.mob-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                switch (sub) {
                    case "remove" -> {
                        plugin.mobs().remove(d);
                        msg(s, "mob.removed", "id", d.id);
                    }
                    case "refresh" -> {
                        plugin.mobs().refresh(d);
                        msg(s, "mob.refreshed", "id", d.id);
                    }
                    default -> msg(s, "cmd.mob-info", "id", d.id, "type", d.entityType, "health", d.health, "damage", d.damage,
                            "model", d.hasModel() ? d.model : "-", "skills", d.skills.size(), "phases", d.phases.size());
                }
            }
            case "model" -> {
                MobDef d = r.length > 2 ? plugin.mobs().get(r[1]) : null;
                if (d == null) {
                    msg(s, "cmd.usage", "usage", "/dimension mob model <id> <modelo|none>");
                    return;
                }
                d.model = r[2].equalsIgnoreCase("none") ? "" : r[2];
                plugin.mobs().changed(d);
                msg(s, "mob.model-set", "model", d.model.isEmpty() ? "-" : d.model, "id", d.id);
            }
            default -> msg(s, "cmd.usage", "usage", "/dimension mob <menu|list|create|spawn|remove|info|model|refresh>");
        }
    }

    // ---------------------------------------------------------------- habilidades

    private void skill(CommandSender s, String[] r) {
        String sub = r.length == 0 ? "" : r[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "library", "menu" -> {
                Player p = player(s);
                if (p != null) {
                    new SkillLibraryMenu(plugin, p, null, null).open();
                }
            }
            case "count" -> msg(s, "cmd.skill-count", "count", plugin.skills().count());
            case "list" -> {
                String q = r.length > 1 ? r[1].toLowerCase(Locale.ROOT) : "";
                List<SkillDef> found = plugin.skills().all().stream()
                        .filter(d -> q.isEmpty() || d.id.contains(q) || d.name.toLowerCase(Locale.ROOT).contains(q))
                        .limit(25).collect(Collectors.toList());
                msg(s, "cmd.skill-list-header", "shown", found.size(), "total", plugin.skills().count());
                for (SkillDef d : found) {
                    msg(s, "cmd.skill-list-entry", "id", d.id, "name", d.name);
                }
            }
            case "info" -> {
                SkillDef d = r.length > 1 ? plugin.skills().get(r[1]) : null;
                if (d == null) {
                    msg(s, "cmd.skill-unknown", "id", r.length > 1 ? r[1] : "?");
                    return;
                }
                msg(s, "cmd.skill-info", "id", d.id, "name", d.name, "desc", d.description, "trigger", d.trigger.label,
                        "shape", d.shape.label, "element", d.element.label, "effect", d.effect.label, "power", d.power,
                        "radius", d.radius, "cooldown", d.cooldown, "tier", d.tier);
            }
            case "regen" -> {
                plugin.skills().regenerate();
                msg(s, "cmd.skill-regen", "count", plugin.skills().count());
            }
            case "test" -> {
                Player p = player(s);
                if (p == null) {
                    return;
                }
                MobDef d = r.length > 2 ? plugin.mobs().get(r[1]) : null;
                SkillDef sk = r.length > 2 ? plugin.skills().get(r[2]) : null;
                if (d == null || sk == null) {
                    msg(s, "cmd.usage", "usage", "/dimension skill test <mob> <habilidad>");
                    return;
                }
                msg(s, plugin.skills().test(p, d, new SkillBinding(sk.id)) ? "skill.test-ok" : "skill.test-failed", "skill", sk.name);
            }
            default -> msg(s, "cmd.usage", "usage", "/dimension skill <library|count|list|info|regen|test>");
        }
    }

    // ---------------------------------------------------------------- pack

    private void pack(CommandSender s, String[] r) {
        String sub = r.length == 0 ? "" : r[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                Player p = player(s);
                if (p != null) {
                    new PackMenu(plugin, p, null).open();
                }
            }
            case "build", "rebuild" -> {
                msg(s, "pack.building");
                plugin.packs().build(s, null);
            }
            case "send" -> {
                if (r.length > 1 && r[1].equalsIgnoreCase("all")) {
                    msg(s, "pack.sent", "count", plugin.packs().sendAll());
                    return;
                }
                Player target = r.length > 1 ? Bukkit.getPlayerExact(r[1]) : s instanceof Player p ? p : null;
                if (target == null) {
                    msg(s, "cmd.player-unknown", "name", r.length > 1 ? r[1] : "?");
                    return;
                }
                msg(s, plugin.packs().send(target) ? "pack.sent" : "pack.no-url", "count", 1);
            }
            case "status" -> msg(s, "cmd.pack-status", "models", plugin.packs().modelCount(), "cmds", plugin.packs().cmds().size(),
                    "hash", plugin.packs().hash().isEmpty() ? "-" : plugin.packs().hash(), "warnings", plugin.packs().warnings().size(),
                    "errors", plugin.packs().errors().size(), "url", plugin.packs().url() == null ? "no configurada" : plugin.packs().url());
            case "validate" -> {
                for (String line : plugin.packs().report()) {
                    s.sendMessage(Text.mm(line));
                }
            }
            default -> msg(s, "cmd.usage", "usage", "/dimension pack <menu|build|send [jugador|all]|status|validate>");
        }
    }

    // ---------------------------------------------------------------- autocompletado

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(p)).sorted().limit(60).collect(Collectors.toList());
    }

    private List<String> materials(String prefix) {
        String p = prefix.toUpperCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isLegacy() && m.name().startsWith(p)) {
                out.add(m.name());
                if (out.size() >= 50) {
                    break;
                }
            }
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("dimensionforge.admin") && !s.hasPermission("dimensionforge.tp")) {
            return List.of();
        }
        if (a.length == 1) {
            return filter(ROOT, a[0]);
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        List<String> dims = new ArrayList<>(plugin.dimensions().names());
        for (World w : Bukkit.getWorlds()) {
            if (!dims.contains(w.getName())) {
                dims.add(w.getName());
            }
        }
        switch (sub) {
            case "tp" -> {
                if (a.length == 2) {
                    return filter(plugin.dimensions().names(), a[1]);
                }
                if (a.length == 3) {
                    return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), a[2]);
                }
            }
            case "portal" -> {
                if (a.length == 2) {
                    return filter(List.of("menu", "create", "list", "tp", "delete"), a[1]);
                }
                if (a.length == 3 && (a[1].equalsIgnoreCase("tp") || a[1].equalsIgnoreCase("delete"))) {
                    return filter(plugin.portals().ids(), a[2]);
                }
            }
            case "ore" -> {
                return tabOre(a, dims);
            }
            case "mob" -> {
                return tabMob(a, dims);
            }
            case "skill" -> {
                if (a.length == 2) {
                    return filter(List.of("library", "count", "list", "info", "regen", "test"), a[1]);
                }
                if (a[1].equalsIgnoreCase("info") && a.length == 3) {
                    return filter(plugin.skills().all().stream().map(d -> d.id).collect(Collectors.toList()), a[2]);
                }
                if (a[1].equalsIgnoreCase("test")) {
                    if (a.length == 3) {
                        return filter(plugin.mobs().all().stream().map(d -> d.id).collect(Collectors.toList()), a[2]);
                    }
                    if (a.length == 4) {
                        return filter(plugin.skills().all().stream().map(d -> d.id).collect(Collectors.toList()), a[3]);
                    }
                }
            }
            case "pack" -> {
                if (a.length == 2) {
                    return filter(List.of("menu", "build", "send", "status", "validate"), a[1]);
                }
                if (a.length == 3 && a[1].equalsIgnoreCase("send")) {
                    List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
                    names.add("all");
                    return filter(names, a[2]);
                }
            }
            default -> {
            }
        }
        return List.of();
    }

    private List<String> tabOre(String[] a, List<String> dims) {
        if (a.length == 2) {
            return filter(List.of("menu", "list", "create", "set", "info", "remove", "enable", "disable", "scan", "place", "regen"), a[1]);
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (a.length == 3) {
            return switch (sub) {
                case "menu", "list" -> filter(dims, a[2]);
                case "set", "info", "remove", "enable", "disable", "scan", "place" -> filter(plugin.ores().ids(), a[2]);
                default -> List.of();
            };
        }
        if (sub.equals("create")) {
            return a.length == 4 ? filter(dims, a[3]) : a.length == 5 ? materials(a[4]) : List.of();
        }
        if (sub.equals("set")) {
            if (a.length == 4) {
                return filter(List.of("drop", "model", "tool", "hardness", "vein"), a[3]);
            }
            String what = a[3].toLowerCase(Locale.ROOT);
            if (what.equals("drop") && a.length == 5) {
                List<String> l = new ArrayList<>(List.of("hand", "clear"));
                l.addAll(materials(a[4]));
                return filter(l, a[4]);
            }
            if (what.equals("model") && a.length == 5) {
                List<String> l = new ArrayList<>(plugin.models().modelNames());
                l.add("none");
                return filter(l, a[4]);
            }
            if (what.equals("tool")) {
                if (a.length == 5) {
                    return filter(List.of("tier", "specific"), a[4]);
                }
                if (a.length == 6) {
                    return a[4].equalsIgnoreCase("tier") ? filter(TIERS, a[5]) : materials(a[5]);
                }
            }
        }
        return List.of();
    }

    private List<String> tabMob(String[] a, List<String> dims) {
        if (a.length == 2) {
            return filter(List.of("menu", "list", "create", "spawn", "remove", "info", "model", "refresh"), a[1]);
        }
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (a.length == 3) {
            return switch (sub) {
                case "menu" -> filter(dims, a[2]);
                case "spawn", "remove", "info", "model", "refresh" ->
                        filter(plugin.mobs().all().stream().map(d -> d.id).collect(Collectors.toList()), a[2]);
                default -> List.of();
            };
        }
        if (sub.equals("create")) {
            if (a.length == 4) {
                return filter(MobManager.validTypes().stream().map(Enum::name).collect(Collectors.toList()), a[3]);
            }
            if (a.length == 5) {
                return filter(dims, a[4]);
            }
        }
        if (sub.equals("model") && a.length == 4) {
            List<String> l = new ArrayList<>(plugin.models().modelNames());
            l.add("none");
            return filter(l, a[3]);
        }
        return List.of();
    }

    /** Pequeña ayuda para validar que hay un ítem en la mano. */
    private static final class ItemStackCheck {
        static final class NoItem extends RuntimeException {
            NoItem() {
                super("sin ítem en la mano");
            }
        }

        static void requireItem(Player p) {
            if (p.getInventory().getItemInMainHand().getType().isAir()) {
                throw new NoItem();
            }
        }
    }
}
