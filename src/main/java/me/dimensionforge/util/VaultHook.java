package me.dimensionforge.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.lang.reflect.Method;

/** Integración opcional con Vault mediante reflexión (sin dependencia en compilación). */
public final class VaultHook {

    private Object economy;
    private Method has;
    private Method withdraw;

    public VaultHook() {
        try {
            if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
                return;
            }
            Class<?> econClass = Class.forName("net.milkbowl.vault.economy.Economy");
            var reg = Bukkit.getServer().getServicesManager().getRegistration(econClass);
            if (reg == null) {
                return;
            }
            economy = reg.getProvider();
            has = econClass.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = econClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
        } catch (Exception ex) {
            economy = null;
        }
    }

    public boolean available() {
        return economy != null;
    }

    public boolean has(OfflinePlayer p, double amount) {
        if (economy == null) {
            return true;
        }
        try {
            return (boolean) has.invoke(economy, p, amount);
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (economy == null) {
            return true;
        }
        try {
            Object response = withdraw.invoke(economy, p, amount);
            Method ok = response.getClass().getMethod("transactionSuccess");
            return (boolean) ok.invoke(response);
        } catch (Exception ex) {
            return false;
        }
    }
}
