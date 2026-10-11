package me.dimensionforge.model;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bukkit.BukkitModelEventBus;
import kr.toxicity.model.api.bukkit.platform.BukkitAdapter;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.tracker.DummyTracker;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.ModelScaler;
import kr.toxicity.model.api.tracker.TrackerModifier;
import me.dimensionforge.DimensionForgePlugin;
import me.dimensionforge.util.Keys;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Integración con BetterModel 2.2.0 (la última línea que soporta Minecraft 1.21.1).
 * Todas las llamadas a la API están contenidas en esta clase: si BetterModel no está, nunca se carga.
 */
public final class BetterModelBridge implements ModelBridge {

    private final DimensionForgePlugin plugin;
    private final List<Runnable> reloadActions = new CopyOnWriteArrayList<>();
    /** Trackers temporales (animaciones de muerte) y entidades auxiliares aún vivas. */
    private final List<AutoCloseable> temporary = new CopyOnWriteArrayList<>();

    public BetterModelBridge(DimensionForgePlugin plugin) {
        this.plugin = plugin;
        // Comprobación temprana: si la API no responde, create() cae al modo simple.
        BetterModel.modelKeys();
        if (BetterModel.eventBus() instanceof BukkitModelEventBus bus) {
            bus.subscribe(plugin, PluginEndReloadEvent.class, event -> {
                // BetterModel recarga sus modelos de forma asíncrona: volvemos al hilo principal.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    for (Runnable r : reloadActions) {
                        try {
                            r.run();
                        } catch (Exception ex) {
                            plugin.getLogger().warning("Error al reaplicar modelos: " + ex);
                        }
                    }
                });
            });
        }
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String provider() {
        return "BetterModel";
    }

    @Override
    public List<String> modelNames() {
        Set<String> keys = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.addAll(BetterModel.modelKeys());
        return new ArrayList<>(keys);
    }

    @Override
    public boolean hasModel(String name) {
        return name != null && !name.isBlank() && BetterModel.modelOrNull(name) != null;
    }

    @Override
    public List<String> animations(String model) {
        ModelRenderer r = renderer(model);
        if (r == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(r.animations().keySet());
        Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    @Override
    public double animationLength(String model, String animation) {
        ModelRenderer r = renderer(model);
        if (r == null || animation == null) {
            return -1;
        }
        return r.animation(animation).map(BlueprintAnimation::length).map(Float::doubleValue).orElse(-1.0);
    }

    @Override
    public boolean apply(LivingEntity entity, String model, double scale) {
        ModelRenderer r = renderer(model);
        if (r == null || entity == null || !entity.isValid()) {
            return false;
        }
        PlatformEntity pe = BukkitAdapter.adapt((Entity) entity);
        EntityTracker tracker = r.getOrCreate(pe, TrackerModifier.DEFAULT);
        tracker.hideOption(EntityHideOption.DEFAULT);
        tracker.scaler(ModelScaler.value((float) Math.max(0.05, scale)));
        return true;
    }

    @Override
    public void remove(Entity entity, String model) {
        EntityTracker t = tracker(entity, model);
        if (t != null && !t.isClosed()) {
            t.close();
        }
    }

    @Override
    public boolean has(Entity entity, String model) {
        EntityTracker t = tracker(entity, model);
        return t != null && !t.isClosed();
    }

    @Override
    public boolean animate(Entity entity, String model, String animation, boolean loop) {
        EntityTracker t = tracker(entity, model);
        if (t == null || animation == null || animation.isBlank()) {
            return false;
        }
        AnimationModifier mod = AnimationModifier.builder()
                .type(loop ? AnimationIterator.Type.LOOP : AnimationIterator.Type.PLAY_ONCE).build();
        return t.animate(animation, mod);
    }

    @Override
    public void stopAnimation(Entity entity, String model, String animation) {
        EntityTracker t = tracker(entity, model);
        if (t != null && animation != null && !animation.isBlank()) {
            t.stopAnimation(animation);
        }
    }

    @Override
    public boolean playDeath(Location where, String model, String animation, double scale) {
        ModelRenderer r = renderer(model);
        if (r == null || where == null || where.getWorld() == null || animation == null || animation.isBlank()) {
            return false;
        }
        PlatformLocation pl = BukkitAdapter.adapt(where);
        DummyTracker tracker = r.create(pl, TrackerModifier.DEFAULT);
        tracker.scaler(ModelScaler.value((float) Math.max(0.05, scale)));
        for (Player p : where.getWorld().getNearbyPlayers(where, 64)) {
            tracker.spawn(BukkitAdapter.adapt(p));
        }
        boolean ok = tracker.animate(animation, AnimationModifier.builder().type(AnimationIterator.Type.PLAY_ONCE).build());
        if (!ok) {
            tracker.close();
            return false;
        }
        double len = animationLength(model, animation);
        long ticks = (long) Math.ceil((len > 0 ? len : 1.5) * 20.0) + 4;
        AutoCloseable handle = tracker::close;
        temporary.add(handle);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            temporary.remove(handle);
            if (!tracker.isClosed()) {
                tracker.close();
            }
        }, ticks);
        return true;
    }

    @Override
    public AutoCloseable spawnStatic(Location where, String model, float scale) {
        ModelRenderer r = renderer(model);
        if (r == null || where == null || where.getWorld() == null) {
            return null;
        }
        // Entidad auxiliar invisible y no persistente que sostiene el modelo (así lo ven los jugadores cercanos
        // automáticamente y no queda nada guardado en disco).
        ArmorStand stand = where.getWorld().spawn(where.clone().subtract(0, 0.5, 0), ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setMarker(true);
            s.setGravity(false);
            s.setSilent(true);
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.setBasePlate(false);
            s.getPersistentDataContainer().set(Keys.ORE_DISPLAY, PersistentDataType.STRING, model);
        });
        PlatformEntity pe = BukkitAdapter.adapt((Entity) stand);
        EntityTracker tracker = r.getOrCreate(pe, TrackerModifier.DEFAULT);
        tracker.hideOption(EntityHideOption.DEFAULT);
        tracker.scaler(ModelScaler.value(Math.max(0.05f, scale)));
        AutoCloseable handle = () -> {
            if (!tracker.isClosed()) {
                tracker.close();
            }
            if (stand.isValid()) {
                stand.remove();
            }
        };
        temporary.add(handle);
        return () -> {
            temporary.remove(handle);
            handle.close();
        };
    }

    @Override
    public void onModelsReloaded(Runnable action) {
        reloadActions.add(action);
    }

    @Override
    public void shutdown() {
        for (AutoCloseable c : new ArrayList<>(temporary)) {
            try {
                c.close();
            } catch (Exception ignored) {
                // cierre best-effort al apagar
            }
        }
        temporary.clear();
        reloadActions.clear();
    }

    private static ModelRenderer renderer(String model) {
        if (model == null || model.isBlank()) {
            return null;
        }
        return BetterModel.modelOrNull(model);
    }

    private static EntityTracker tracker(Entity entity, String model) {
        if (entity == null || model == null) {
            return null;
        }
        PlatformEntity pe = BukkitAdapter.adapt(entity);
        EntityTrackerRegistry reg = BetterModel.registryOrNull(pe);
        if (reg == null) {
            return null;
        }
        return reg.tracker(model);
    }
}
