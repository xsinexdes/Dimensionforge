package me.dimensionforge.model;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;

import java.util.List;

/** Implementación vacía: se usa cuando BetterModel no está instalado. */
public final class SimpleModelBridge implements ModelBridge {

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String provider() {
        return "ninguno";
    }

    @Override
    public List<String> modelNames() {
        return List.of();
    }

    @Override
    public boolean hasModel(String name) {
        return false;
    }

    @Override
    public List<String> animations(String model) {
        return List.of();
    }

    @Override
    public double animationLength(String model, String animation) {
        return -1;
    }

    @Override
    public boolean apply(LivingEntity entity, String model, double scale) {
        return false;
    }

    @Override
    public void remove(Entity entity, String model) {
    }

    @Override
    public boolean has(Entity entity, String model) {
        return false;
    }

    @Override
    public boolean animate(Entity entity, String model, String animation, boolean loop) {
        return false;
    }

    @Override
    public void stopAnimation(Entity entity, String model, String animation) {
    }

    @Override
    public boolean playDeath(Location where, String model, String animation, double scale) {
        return false;
    }

    @Override
    public AutoCloseable spawnStatic(Location where, String model, float scale) {
        return null;
    }

    @Override
    public void onModelsReloaded(Runnable action) {
    }

    @Override
    public void shutdown() {
    }
}
