package me.dimensionforge.mob;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Estado en ejecución de un mob custom vivo. */
public final class MobState {
    public final UUID id;
    public final LivingEntity entity;
    public final MobDef def;
    /** Modelo aplicado ahora mismo ("" = ninguno). */
    public String appliedModel = "";
    public int phase = -1;
    public double damageMultiplier = 1.0;
    public double speedMultiplier = 1.0;
    public BossBar bar;
    public final Set<UUID> viewers = new HashSet<>();
    public long nextAmbientTick;
    public Location lastLocation;
    public String animState = "";
    public long animLockUntilTick;
    /** Tick de aparición/carga (para el disparador ON_SPAWN y los tiempos IDLE). */
    public long bornTick;
    public long idleSinceTick;
    public boolean dying;

    public MobState(LivingEntity entity, MobDef def) {
        this.id = entity.getUniqueId();
        this.entity = entity;
        this.def = def;
    }
}
