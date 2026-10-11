package me.dimensionforge.skill.model;

/** Elementos con partículas, sonido y color por defecto. */
public enum Element {
    FIRE("Fuego", "Ardiente", "Ardiente", "FLAME", "entity.blaze.shoot", "#FF6A00", Effect.BURN),
    ICE("Hielo", "Helado", "Helada", "SNOWFLAKE", "block.glass.break", "#9BE8FF", Effect.SLOW),
    LIGHTNING("Rayo", "Eléctrico", "Eléctrica", "ELECTRIC_SPARK", "entity.lightning_bolt.thunder", "#FFF44F", Effect.STUN),
    POISON("Veneno", "Venenoso", "Venenosa", "ITEM_SLIME", "entity.witch.drink", "#6BBF3A", Effect.POISON),
    SHADOW("Sombra", "Sombrío", "Sombría", "SMOKE", "entity.enderman.teleport", "#3B1F5C", Effect.DARKNESS),
    LIGHT("Luz", "Radiante", "Radiante", "END_ROD", "block.beacon.activate", "#FFF9C4", Effect.GLOW),
    EARTH("Tierra", "Pétreo", "Pétrea", "COMPOSTER", "block.rooted_dirt.break", "#8B5A2B", Effect.ROOT),
    WIND("Viento", "Huracanado", "Huracanada", "CLOUD", "entity.phantom.flap", "#D6F5F0", Effect.KNOCKBACK),
    BLOOD("Sangre", "Sangriento", "Sangrienta", "CRIMSON_SPORE", "entity.wither.hurt", "#B00020", Effect.BLEED),
    ARCANE("Arcano", "Arcano", "Arcana", "ENCHANT", "block.enchantment_table.use", "#B388FF", Effect.MARK);

    public final String label;
    public final String adjMasc;
    public final String adjFem;
    public final String particle;
    public final String sound;
    public final String color;
    public final Effect rider;

    Element(String label, String adjMasc, String adjFem, String particle, String sound, String color, Effect rider) {
        this.label = label;
        this.adjMasc = adjMasc;
        this.adjFem = adjFem;
        this.particle = particle;
        this.sound = sound;
        this.color = color;
        this.rider = rider;
    }

    public String adjective(boolean feminine) {
        return feminine ? adjFem : adjMasc;
    }
}
