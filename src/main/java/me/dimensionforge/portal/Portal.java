package me.dimensionforge.portal;

import me.dimensionforge.dimension.DimensionData;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

/** Un portal: región cúbica + destino + opciones. */
public final class Portal {

    public enum FillMode { NONE, END_GATEWAY, NETHER_PORTAL, BLOCK }

    public enum Trigger { WALK, CLICK }

    public final String id;
    public String world = "";
    public int x1, y1, z1, x2, y2, z2;

    public String destination = "";
    public boolean arrivalSpawn = true;
    public double ax, ay, az;
    public float ayaw, apitch;

    public String returnPortal = "";

    public FillMode fillMode = FillMode.NONE;
    public String fillMaterial = "STONE";
    /** Material realmente colocado (para poder limpiar el relleno anterior). */
    public String filledMaterial = "";
    public boolean protectFill = true;

    public boolean particles = true;
    public String particleType = "PORTAL";
    public String sound = "entity.enderman.teleport";

    public int cooldown = 3;
    public String permission = "";
    public String costMaterial = "";
    public int costAmount = 0;
    public double costMoney = 0;

    public String title = "";
    public String message = "";
    public Trigger trigger = Trigger.WALK;

    public Portal(String id) {
        this.id = id;
    }

    public void setRegion(String world, int ax, int ay, int az, int bx, int by, int bz) {
        this.world = world;
        this.x1 = Math.min(ax, bx);
        this.y1 = Math.min(ay, by);
        this.z1 = Math.min(az, bz);
        this.x2 = Math.max(ax, bx);
        this.y2 = Math.max(ay, by);
        this.z2 = Math.max(az, bz);
    }

    public boolean contains(String w, int x, int y, int z) {
        return world.equals(w) && x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
    }

    public boolean contains(Location l) {
        return l.getWorld() != null && contains(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public long volume() {
        return (long) (x2 - x1 + 1) * (y2 - y1 + 1) * (z2 - z1 + 1);
    }

    public double centerX() {
        return (x1 + x2 + 1) / 2.0;
    }

    public double centerZ() {
        return (z1 + z2 + 1) / 2.0;
    }

    /** Copia todos los ajustes (menos el id). */
    public void copyFrom(Portal o) {
        world = o.world;
        x1 = o.x1;
        y1 = o.y1;
        z1 = o.z1;
        x2 = o.x2;
        y2 = o.y2;
        z2 = o.z2;
        destination = o.destination;
        arrivalSpawn = o.arrivalSpawn;
        ax = o.ax;
        ay = o.ay;
        az = o.az;
        ayaw = o.ayaw;
        apitch = o.apitch;
        returnPortal = o.returnPortal;
        fillMode = o.fillMode;
        fillMaterial = o.fillMaterial;
        filledMaterial = o.filledMaterial;
        protectFill = o.protectFill;
        particles = o.particles;
        particleType = o.particleType;
        sound = o.sound;
        cooldown = o.cooldown;
        permission = o.permission;
        costMaterial = o.costMaterial;
        costAmount = o.costAmount;
        costMoney = o.costMoney;
        title = o.title;
        message = o.message;
        trigger = o.trigger;
    }

    public void save(ConfigurationSection s) {
        s.set("world", world);
        s.set("region", x1 + "," + y1 + "," + z1 + "," + x2 + "," + y2 + "," + z2);
        s.set("destination", destination);
        s.set("arrival.spawn", arrivalSpawn);
        s.set("arrival.x", ax);
        s.set("arrival.y", ay);
        s.set("arrival.z", az);
        s.set("arrival.yaw", (double) ayaw);
        s.set("arrival.pitch", (double) apitch);
        s.set("return-portal", returnPortal);
        s.set("fill.mode", fillMode.name());
        s.set("fill.material", fillMaterial);
        s.set("fill.placed", filledMaterial);
        s.set("fill.protect", protectFill);
        s.set("particles.enabled", particles);
        s.set("particles.type", particleType);
        s.set("sound", sound);
        s.set("cooldown", cooldown);
        s.set("permission", permission);
        s.set("cost.material", costMaterial);
        s.set("cost.amount", costAmount);
        s.set("cost.money", costMoney);
        s.set("title", title);
        s.set("message", message);
        s.set("trigger", trigger.name());
    }

    public static Portal load(String id, ConfigurationSection s) {
        Portal p = new Portal(id);
        p.world = s.getString("world", "");
        String[] r = s.getString("region", "0,0,0,0,0,0").split(",");
        try {
            p.x1 = Integer.parseInt(r[0].trim());
            p.y1 = Integer.parseInt(r[1].trim());
            p.z1 = Integer.parseInt(r[2].trim());
            p.x2 = Integer.parseInt(r[3].trim());
            p.y2 = Integer.parseInt(r[4].trim());
            p.z2 = Integer.parseInt(r[5].trim());
        } catch (RuntimeException ex) {
            p.x1 = p.y1 = p.z1 = p.x2 = p.y2 = p.z2 = 0;
        }
        p.destination = s.getString("destination", "");
        p.arrivalSpawn = s.getBoolean("arrival.spawn", true);
        p.ax = s.getDouble("arrival.x");
        p.ay = s.getDouble("arrival.y");
        p.az = s.getDouble("arrival.z");
        p.ayaw = (float) s.getDouble("arrival.yaw");
        p.apitch = (float) s.getDouble("arrival.pitch");
        p.returnPortal = s.getString("return-portal", "");
        p.fillMode = DimensionData.enumOf(FillMode.class, s.getString("fill.mode"), FillMode.NONE);
        p.fillMaterial = s.getString("fill.material", "STONE");
        p.filledMaterial = s.getString("fill.placed", "");
        p.protectFill = s.getBoolean("fill.protect", true);
        p.particles = s.getBoolean("particles.enabled", true);
        p.particleType = s.getString("particles.type", "PORTAL");
        p.sound = s.getString("sound", "entity.enderman.teleport");
        p.cooldown = s.getInt("cooldown", 3);
        p.permission = s.getString("permission", "");
        p.costMaterial = s.getString("cost.material", "");
        p.costAmount = s.getInt("cost.amount", 0);
        p.costMoney = s.getDouble("cost.money", 0);
        p.title = s.getString("title", "");
        p.message = s.getString("message", "");
        p.trigger = DimensionData.enumOf(Trigger.class, s.getString("trigger"), Trigger.WALK);
        return p;
    }
}
