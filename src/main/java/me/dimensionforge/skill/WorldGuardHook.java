package me.dimensionforge.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * Integración opcional con WorldGuard mediante reflexión (así no hace falta compilar contra su API).
 * Si WorldGuard no está o algo falla, todo se permite.
 */
public final class WorldGuardHook {

    private final boolean enabled;
    private Object regionContainer;
    private Method createQuery;
    private Method testState;
    private Method adaptLocation;
    private Object flagMobDamage;
    private Object flagBuild;
    private Class<?> stateFlagArrayClass;

    public WorldGuardHook(Logger log) {
        boolean ok = false;
        if (Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            try {
                Class<?> wg = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object inst = wg.getMethod("getInstance").invoke(null);
                Object platform = inst.getClass().getMethod("getPlatform").invoke(inst);
                regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                createQuery = regionContainer.getClass().getMethod("createQuery");
                Class<?> wgLoc = Class.forName("com.sk89q.worldedit.util.Location");
                Class<?> assoc = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
                Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
                stateFlagArrayClass = java.lang.reflect.Array.newInstance(stateFlag, 0).getClass();
                Class<?> query = createQuery.getReturnType();
                testState = query.getMethod("testState", wgLoc, assoc, stateFlagArrayClass);
                Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                adaptLocation = adapter.getMethod("adapt", Location.class);
                Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                flagMobDamage = flags.getField("MOB_DAMAGE").get(null);
                flagBuild = flags.getField("BUILD").get(null);
                ok = true;
                log.info("WorldGuard detectado: las habilidades respetan mob-damage y build.");
            } catch (Throwable t) {
                log.warning("WorldGuard presente pero no se pudo enlazar (" + t + "). Se ignoran sus regiones.");
            }
        }
        this.enabled = ok;
    }

    public boolean active() {
        return enabled;
    }

    /** ¿Pueden los mobs hacer daño en esta posición? (flag mob-damage de WorldGuard). */
    public boolean allowMobDamage(Location loc) {
        return test(loc, flagMobDamage);
    }

    /** ¿Se pueden colocar bloques temporales aquí? (flag build para un no-miembro). */
    public boolean allowBlockChange(Location loc) {
        return test(loc, flagBuild);
    }

    private boolean test(Location loc, Object flag) {
        if (!enabled) {
            return true;
        }
        try {
            Object query = createQuery.invoke(regionContainer);
            Object wgLoc = adaptLocation.invoke(null, loc);
            Object arr = java.lang.reflect.Array.newInstance(stateFlagArrayClass.getComponentType(), 1);
            java.lang.reflect.Array.set(arr, 0, flag);
            return (boolean) testState.invoke(query, wgLoc, null, arr);
        } catch (Throwable t) {
            return true;
        }
    }
}
