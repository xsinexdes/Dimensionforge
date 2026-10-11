package me.dimensionforge.skill.model;

import java.util.EnumSet;
import java.util.Set;

/** Efectos que puede aplicar una habilidad (52). */
public enum Effect {
    DAMAGE("Daño directo", "", Category.DAMAGE, Kind.TARGET),
    DAMAGE_OVER_TIME("Daño sobre tiempo", "Agonía", Category.DAMAGE, Kind.TARGET),
    HEAL("Curación", "Vida", Category.SUPPORT, Kind.SUPPORT),
    LIFESTEAL("Robo de vida", "Sed", Category.DAMAGE, Kind.TARGET),
    KNOCKBACK("Empujar", "Fuerza", Category.CONTROL, Kind.TARGET),
    PULL("Atraer", "Atracción", Category.CONTROL, Kind.TARGET),
    LAUNCH_UP("Lanzar al aire", "Ascenso", Category.CONTROL, Kind.TARGET),
    SLOW("Ralentizar", "Lentitud", Category.CONTROL, Kind.TARGET),
    FREEZE("Congelar", "Escarcha", Category.CONTROL, Kind.TARGET),
    BURN("Quemar", "Brasas", Category.DAMAGE, Kind.TARGET),
    POISON("Envenenar", "Ponzoña", Category.DEBUFF, Kind.TARGET),
    BLIND("Ceguera", "Ceguera", Category.DEBUFF, Kind.TARGET),
    NAUSEA("Náusea", "Mareo", Category.DEBUFF, Kind.TARGET),
    WEAKNESS("Debilidad", "Flaqueza", Category.DEBUFF, Kind.TARGET),
    HUNGER("Hambre", "Hambruna", Category.DEBUFF, Kind.TARGET),
    LEVITATE("Levitación", "Levitación", Category.CONTROL, Kind.TARGET),
    MINING_FATIGUE("Fatiga de minería", "Fatiga", Category.DEBUFF, Kind.TARGET),
    ROOT("Inmovilizar", "Raíces", Category.CONTROL, Kind.TARGET),
    SILENCE("Silenciar", "Silencio", Category.DEBUFF, Kind.TARGET),
    DISARM("Quitar ítem de la mano", "Desarme", Category.DEBUFF, Kind.TARGET),
    DURABILITY_DAMAGE("Dañar durabilidad", "Óxido", Category.DEBUFF, Kind.TARGET),
    SUMMON_MOBS("Invocar mobs", "Legión", Category.SUMMON, Kind.POINT),
    TEMP_COBWEB("Telaraña temporal", "Telarañas", Category.CONTROL, Kind.POINT),
    TEMP_ICE("Hielo temporal", "Hielo", Category.CONTROL, Kind.POINT),
    TEMP_LAVA("Fuego temporal", "Brasas vivas", Category.DAMAGE, Kind.POINT),
    EXPLOSION_NO_BLOCKS("Explosión sin romper bloques", "Detonación", Category.DAMAGE, Kind.POINT),
    LIGHTNING("Relámpago", "Relámpagos", Category.DAMAGE, Kind.POINT),
    ABSORPTION("Absorción", "Absorción", Category.SUPPORT, Kind.SUPPORT),
    INVISIBILITY("Invisibilidad", "Sigilo", Category.SUPPORT, Kind.SUPPORT),
    SPEED("Velocidad", "Celeridad", Category.SUPPORT, Kind.SUPPORT),
    STRENGTH("Fuerza", "Poderío", Category.SUPPORT, Kind.SUPPORT),
    REFLECT_DAMAGE("Reflejar daño", "Espejo", Category.SUPPORT, Kind.CASTER),
    SWARM("Enjambre", "Plaga", Category.SUMMON, Kind.POINT),
    CLONES("Clones", "Ilusiones", Category.SUMMON, Kind.CASTER),
    MODEL_ANIMATION("Cambiar animación del modelo", "Pose", Category.UTILITY, Kind.CASTER),
    SOUND("Sonido", "Eco", Category.UTILITY, Kind.POINT),
    PARTICLES("Partículas", "Destellos", Category.UTILITY, Kind.POINT),
    TITLE("Título en pantalla", "Presagio", Category.UTILITY, Kind.TARGET),
    PULL_TO_POINT("Empujar hacia un punto", "Vórtice", Category.CONTROL, Kind.TARGET),
    WITHER("Marchitar", "Marchitez", Category.DEBUFF, Kind.TARGET),
    DARKNESS("Oscuridad", "Penumbra", Category.DEBUFF, Kind.TARGET),
    GLOW("Revelar (resplandor)", "Revelación", Category.DEBUFF, Kind.TARGET),
    RESISTANCE("Resistencia", "Coraza", Category.SUPPORT, Kind.SUPPORT),
    REGENERATION("Regeneración", "Renacer", Category.SUPPORT, Kind.SUPPORT),
    JUMP_BOOST("Salto mejorado", "Brinco", Category.SUPPORT, Kind.SUPPORT),
    FIRE_RESISTANCE("Resistencia al fuego", "Ceniza", Category.SUPPORT, Kind.SUPPORT),
    SHIELD("Escudo (absorción + resistencia)", "Égida", Category.SUPPORT, Kind.SUPPORT),
    SHOCKWAVE("Onda de choque", "Sacudida", Category.DAMAGE, Kind.POINT),
    STUN("Aturdir", "Aturdimiento", Category.CONTROL, Kind.TARGET),
    MARK("Marcar (más daño recibido)", "Condena", Category.DEBUFF, Kind.TARGET),
    DRAIN_XP("Drenar experiencia", "Saqueo", Category.DEBUFF, Kind.TARGET),
    SCARE("Aterrorizar", "Terror", Category.CONTROL, Kind.TARGET),
    BLEED("Sangrado", "Hemorragia", Category.DAMAGE, Kind.TARGET);

    public enum Category {
        DAMAGE("Daño"), CONTROL("Control"), DEBUFF("Debilitamiento"), SUPPORT("Apoyo"), SUMMON("Invocación"),
        MOVEMENT("Movilidad"), UTILITY("Utilidad");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    /** A quién / dónde se aplica el efecto. */
    public enum Kind { TARGET, SUPPORT, POINT, CASTER }

    public final String label;
    /** Palabra para el complemento del nombre ("... de Fatiga"). Vacío = es el efecto por defecto. */
    public final String word;
    public final Category category;
    public final Kind kind;

    Effect(String label, String word, Category category, Kind kind) {
        this.label = label;
        this.word = word;
        this.category = category;
        this.kind = kind;
    }

    public boolean hostile() {
        return kind == Kind.TARGET || kind == Kind.POINT && this != SOUND && this != PARTICLES && this != SUMMON_MOBS
                && this != SWARM;
    }

    /** Elementos con los que tiene sentido combinar este efecto. */
    public Set<Element> allowedElements() {
        return switch (this) {
            case FREEZE, TEMP_ICE -> EnumSet.of(Element.ICE, Element.ARCANE);
            case BURN, TEMP_LAVA, FIRE_RESISTANCE -> EnumSet.of(Element.FIRE, Element.ARCANE, Element.EARTH);
            case POISON -> EnumSet.of(Element.POISON, Element.BLOOD, Element.SHADOW);
            case LIGHTNING -> EnumSet.of(Element.LIGHTNING, Element.ARCANE);
            case STUN -> EnumSet.of(Element.LIGHTNING, Element.EARTH, Element.ARCANE, Element.WIND);
            case BLIND, DARKNESS -> EnumSet.of(Element.SHADOW, Element.LIGHT, Element.ARCANE);
            case TEMP_COBWEB -> EnumSet.of(Element.POISON, Element.SHADOW, Element.EARTH);
            case HEAL, REGENERATION -> EnumSet.of(Element.LIGHT, Element.EARTH, Element.ARCANE, Element.BLOOD, Element.WIND);
            case LIFESTEAL, BLEED, DRAIN_XP -> EnumSet.of(Element.BLOOD, Element.SHADOW, Element.ARCANE);
            case KNOCKBACK, PULL, LAUNCH_UP, PULL_TO_POINT, SHOCKWAVE, SCARE ->
                    EnumSet.of(Element.WIND, Element.EARTH, Element.ARCANE, Element.LIGHTNING, Element.FIRE);
            case LEVITATE, JUMP_BOOST -> EnumSet.of(Element.WIND, Element.ARCANE, Element.LIGHT);
            case SLOW, ROOT -> EnumSet.of(Element.ICE, Element.EARTH, Element.POISON, Element.SHADOW, Element.ARCANE);
            case WITHER -> EnumSet.of(Element.SHADOW, Element.POISON, Element.BLOOD);
            case GLOW, MARK -> EnumSet.of(Element.LIGHT, Element.ARCANE, Element.BLOOD, Element.LIGHTNING);
            case SPEED -> EnumSet.of(Element.WIND, Element.LIGHTNING, Element.LIGHT, Element.ARCANE);
            case STRENGTH -> EnumSet.of(Element.FIRE, Element.BLOOD, Element.EARTH, Element.LIGHT, Element.ARCANE);
            case RESISTANCE, SHIELD, ABSORPTION -> EnumSet.of(Element.EARTH, Element.LIGHT, Element.ICE, Element.ARCANE, Element.SHADOW);
            case INVISIBILITY -> EnumSet.of(Element.SHADOW, Element.WIND, Element.ARCANE);
            case REFLECT_DAMAGE -> EnumSet.of(Element.LIGHT, Element.ARCANE, Element.ICE, Element.LIGHTNING);
            case SWARM -> EnumSet.of(Element.POISON, Element.SHADOW, Element.BLOOD, Element.EARTH);
            case CLONES -> EnumSet.of(Element.SHADOW, Element.ARCANE, Element.LIGHT, Element.WIND);
            case EXPLOSION_NO_BLOCKS -> EnumSet.of(Element.FIRE, Element.ARCANE, Element.LIGHTNING, Element.EARTH, Element.BLOOD);
            default -> EnumSet.allOf(Element.class);
        };
    }
}
