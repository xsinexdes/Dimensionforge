package me.dimensionforge.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Constructor fluido de ítems para menús. */
public final class ItemBuilder {

    private final ItemStack stack;
    private String name;
    private final List<String> lore = new ArrayList<>();
    private Integer cmd;
    private boolean glow;

    private ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material == null ? Material.STONE : material, Math.max(1, Math.min(99, amount)));
    }

    public static ItemBuilder of(Material material) {
        return new ItemBuilder(material, 1);
    }

    public static ItemBuilder of(Material material, int amount) {
        return new ItemBuilder(material, amount);
    }

    public ItemBuilder name(String mm) {
        this.name = mm;
        return this;
    }

    public ItemBuilder lore(String... lines) {
        lore.addAll(Arrays.asList(lines));
        return this;
    }

    public ItemBuilder lore(List<String> lines) {
        lore.addAll(lines);
        return this;
    }

    public ItemBuilder cmd(Integer cmd) {
        this.cmd = cmd;
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        this.glow = glow;
        return this;
    }

    public ItemStack build() {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.displayName(Text.mm(name));
            }
            if (!lore.isEmpty()) {
                meta.lore(Text.mm(lore));
            }
            if (cmd != null) {
                meta.setCustomModelData(cmd);
            }
            if (glow) {
                meta.setEnchantmentGlintOverride(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP,
                    ItemFlag.HIDE_UNBREAKABLE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
