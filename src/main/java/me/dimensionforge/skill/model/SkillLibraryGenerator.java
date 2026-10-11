package me.dimensionforge.skill.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Genera la biblioteca de habilidades combinando piezas: disparador x forma x elemento x efecto x modificadores.
 * Es determinista: siempre produce la misma lista. No usa Bukkit para poder probarse con JUnit.
 */
public final class SkillLibraryGenerator {
    private SkillLibraryGenerator() {
    }

    /** Efectos que no se usan como efecto principal (solo como secundarios o desde habilidades de firma). */
    private static final Set<Effect> SECONDARY_ONLY = EnumSet.of(Effect.MODEL_ANIMATION, Effect.SOUND, Effect.PARTICLES,
            Effect.TITLE);

    /** Número de elementos por pareja forma/efecto. */
    private static final int ELEMENTS_PER_PAIR = 2;

    public static boolean compatible(Shape shape, Effect effect) {
        if (SECONDARY_ONLY.contains(effect)) return false;
        return switch (shape.kind) {
            case SELF -> effect.kind == Effect.Kind.SUPPORT || effect.kind == Effect.Kind.CASTER
                    && effect != Effect.CLONES;
            case ALLIES -> effect.kind == Effect.Kind.SUPPORT;
            case SUMMON -> effect.category == Effect.Category.SUMMON;
            default -> (effect.kind == Effect.Kind.TARGET || effect.kind == Effect.Kind.POINT && effect.hostile())
                    && effect.category != Effect.Category.SUMMON
                    && !(effect.kind == Effect.Kind.TARGET && effect.category == Effect.Category.SUPPORT);
        };
    }

    public static List<Trigger> allowedTriggers(Shape shape) {
        List<Trigger> out = new ArrayList<>();
        for (Trigger t : Trigger.values()) {
            switch (shape.kind) {
                case SELF, ALLIES -> {
                    if (t != Trigger.ON_DEATH || shape.kind == Shape.Kind.ALLIES) out.add(t);
                }
                case MOVEMENT -> {
                    if (t != Trigger.ON_DEATH && t != Trigger.ON_SPAWN && t != Trigger.HEALTH_ABOVE) out.add(t);
                }
                case AREA, GROUND, SUMMON -> out.add(t);
                default -> {
                    if (t != Trigger.ON_DEATH) out.add(t);
                }
            }
        }
        return out;
    }

    public static List<SkillDef> generate() {
        List<SkillDef> out = new ArrayList<>();
        Set<String> names = new HashSet<>();
        int counter = 0;
        int pairIndex = 0;
        for (Shape shape : Shape.values()) {
            List<Trigger> triggers = allowedTriggers(shape);
            for (Effect effect : Effect.values()) {
                if (!compatible(shape, effect)) continue;
                List<Element> els = new ArrayList<>(effect.allowedElements());
                els.sort(java.util.Comparator.comparingInt(Enum::ordinal));
                int take = Math.min(ELEMENTS_PER_PAIR, els.size());
                for (int k = 0; k < take; k++) {
                    Element element = els.get((pairIndex + k) % els.size());
                    counter++;
                    out.add(build(shape, effect, element, triggers, counter, names));
                }
                pairIndex++;
            }
        }
        return out;
    }

    private static SkillDef build(Shape shape, Effect effect, Element element, List<Trigger> triggers, int n,
                                  Set<String> names) {
        SkillDef d = new SkillDef();
        d.id = shape.name().toLowerCase(Locale.ROOT) + "_" + effect.name().toLowerCase(Locale.ROOT) + "_"
                + element.name().toLowerCase(Locale.ROOT);
        d.shape = shape;
        d.effect = effect;
        d.element = element;
        d.trigger = triggers.get((n * 7) % triggers.size());
        d.triggerValue = triggerValue(d.trigger, n);

        // Modificadores: ciclo que usa los 12; cada 11ª habilidad lleva dos.
        Modifier[] mods = Modifier.values();
        int mi = n % (mods.length + 8);
        if (mi < mods.length) {
            d.modifiers.add(mods[mi]);
            if (n % 11 == 0) {
                Modifier second = mods[(mi + 5) % mods.length];
                if (second != mods[mi]) d.modifiers.add(second);
            }
        }

        // Efecto secundario: el "pasajero" del elemento en una de cada tres habilidades hostiles.
        if (n % 3 == 0 && effect.hostile() && element.rider != effect && element.rider.kind == Effect.Kind.TARGET
                && shape.kind != Shape.Kind.SUMMON) {
            d.secondary = element.rider;
        }

        baseNumbers(d, n);
        for (Modifier m : d.modifiers) {
            d.power *= m.powerMul;
            d.radius *= m.radiusMul;
            d.cooldown *= m.cooldownMul;
            d.duration *= m.durationMul;
            d.count = (int) Math.max(1, Math.round(d.count * m.countMul));
        }
        d.power = clamp(d.power, 0.5, 40);
        d.radius = clamp(d.radius, 1.5, 14);
        d.cooldown = clamp(d.cooldown, 2, 90);
        d.duration = clamp(d.duration, 1, 30);
        d.count = Math.min(d.count, 24);
        d.chance = d.trigger == Trigger.ON_DAMAGED || d.trigger == Trigger.ON_ATTACK
                || d.trigger == Trigger.ON_CRIT || d.trigger == Trigger.HIT_BY_WEAPON ? 0.35 : 1.0;
        if (effect == Effect.SUMMON_MOBS || effect == Effect.SWARM) d.extra = summonType(element, effect);
        if (d.trigger == Trigger.HIT_BY_WEAPON) {
            d.weapon = new String[]{"SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "TRIDENT", "MACE", "BOW"}[n % 8];
        }
        d.tier = tier(d);
        d.name = uniqueName(d, n, names);
        d.description = describe(d);
        return d;
    }

    private static double triggerValue(Trigger t, int n) {
        return switch (t) {
            case INTERVAL -> new double[]{6, 8, 10, 12, 15, 20}[n % 6];
            case HEALTH_BELOW -> new double[]{75, 50, 35, 25}[n % 4];
            case HEALTH_ABOVE -> new double[]{90, 80, 70}[n % 3];
            case IDLE -> new double[]{3, 5, 8}[n % 3];
            case PLAYER_NEAR -> new double[]{4, 6, 8, 10}[n % 4];
            case PLAYER_FAR -> new double[]{12, 16, 20}[n % 3];
            default -> t.defaultValue;
        };
    }

    private static void baseNumbers(SkillDef d, int n) {
        Effect e = d.effect;
        double power = switch (e) {
            case DAMAGE -> 7;
            case LIGHTNING -> 9;
            case EXPLOSION_NO_BLOCKS -> 8;
            case SHOCKWAVE -> 6;
            case BURN, BLEED, DAMAGE_OVER_TIME -> 3;
            case LIFESTEAL -> 5;
            case HEAL -> 8;
            case TEMP_LAVA -> 4;
            case SUMMON_MOBS -> 3;
            case SWARM -> 4;
            case CLONES -> 2;
            case DURABILITY_DAMAGE -> 5;
            case DRAIN_XP -> 4;
            case REFLECT_DAMAGE -> 30;
            default -> 3;
        };
        double radius = 5, range = 16, cooldown = 10, duration = 5;
        int count = 1;
        double pm = 1;
        switch (d.shape) {
            case SELF -> { radius = 3; range = 0; cooldown = 14; }
            case TARGET_PLAYER -> { pm = 1.2; range = 20; cooldown = 8; }
            case NEAREST_PLAYER -> { pm = 1.0; range = 12; cooldown = 7; }
            case ALL_IN_RADIUS -> { pm = 0.8; radius = 7; cooldown = 14; }
            case ALLIES -> { radius = 10; cooldown = 16; }
            case PROJECTILE_STRAIGHT -> { pm = 1.1; range = 24; cooldown = 6; }
            case PROJECTILE_HOMING -> { pm = 0.95; range = 22; cooldown = 9; }
            case BEAM_LINE -> { pm = 1.0; range = 18; radius = 1.5; cooldown = 10; }
            case CONE -> { pm = 0.9; range = 9; radius = 6; cooldown = 10; }
            case CIRCLE_WAVE -> { pm = 0.85; radius = 8; cooldown = 12; }
            case RING -> { pm = 0.8; radius = 7; cooldown = 12; }
            case SKY_RAIN -> { pm = 0.5; radius = 6; count = 8; cooldown = 16; }
            case GROUND_UNDER_TARGET -> { pm = 1.1; radius = 3.5; range = 20; cooldown = 9; }
            case METEORS -> { pm = 0.6; radius = 3; count = 5; range = 18; cooldown = 18; }
            case SUMMON_CIRCLE -> { radius = 4; count = 3; cooldown = 30; duration = 25; }
            case LEAP -> { pm = 1.0; radius = 4; range = 14; cooldown = 12; }
            case CHARGE -> { pm = 1.1; radius = 2.5; range = 14; cooldown = 11; }
            case TELEPORT_BEHIND -> { pm = 1.1; radius = 3; range = 20; cooldown = 12; }
        }
        // Los efectos de estado duran más cuanto más fuertes son.
        if (e.category == Effect.Category.CONTROL || e.category == Effect.Category.DEBUFF) duration = 4 + n % 4;
        if (e.category == Effect.Category.SUPPORT) duration = 8 + n % 5;
        if (e == Effect.SUMMON_MOBS || e == Effect.SWARM || e == Effect.CLONES) {
            count = e == Effect.SWARM ? 6 : e == Effect.CLONES ? 2 : 3;
            duration = 20;
            cooldown = Math.max(cooldown, 30);
        }
        if (e == Effect.TEMP_COBWEB || e == Effect.TEMP_ICE || e == Effect.TEMP_LAVA) duration = 6 + n % 4;
        d.power = power * pm;
        d.radius = radius;
        d.range = range;
        d.cooldown = cooldown;
        d.duration = duration;
        d.count = count;
    }

    private static String summonType(Element el, Effect e) {
        if (e == Effect.SWARM) {
            return switch (el) {
                case POISON -> "CAVE_SPIDER";
                case SHADOW -> "SILVERFISH";
                case BLOOD -> "ENDERMITE";
                default -> "SILVERFISH";
            };
        }
        return switch (el) {
            case FIRE -> "BLAZE";
            case ICE -> "STRAY";
            case LIGHTNING -> "HUSK";
            case POISON -> "SPIDER";
            case SHADOW -> "VEX";
            case LIGHT -> "SKELETON";
            case EARTH -> "ZOMBIE";
            case WIND -> "VEX";
            case BLOOD -> "ZOMBIE";
            case ARCANE -> "VEX";
        };
    }

    static int tier(SkillDef d) {
        double eff = d.power * Math.max(1, Math.min(d.count, 6)) / Math.max(2, d.cooldown);
        if (d.effect.category == Effect.Category.SUMMON) eff = d.count * 0.6 / (d.cooldown / 10.0);
        if (d.effect.category == Effect.Category.CONTROL || d.effect.category == Effect.Category.DEBUFF) {
            eff = (d.power + d.duration) / Math.max(2, d.cooldown) * 1.2;
        }
        if (d.effect.category == Effect.Category.SUPPORT) eff = (d.power + d.duration) / Math.max(2, d.cooldown);
        if (eff < 0.5) return 1;
        if (eff < 1.0) return 2;
        if (eff < 1.6) return 3;
        if (eff < 2.4) return 4;
        return 5;
    }

    private static String uniqueName(SkillDef d, int n, Set<String> names) {
        boolean odd = n % 2 == 1;
        String noun = odd ? d.shape.noun2 : d.shape.noun1;
        boolean fem = odd ? d.shape.fem2 : d.shape.fem1;
        String base = noun + " " + d.element.adjective(fem);
        if (!d.effect.word.isEmpty()) base += " de " + d.effect.word;
        if (!d.modifiers.isEmpty()) base += " · " + d.modifiers.get(0).tag;
        String name = base;
        if (!names.add(name)) {
            name = base + " " + d.trigger.qualifier();
            if (!names.add(name)) {
                int i = 2;
                while (!names.add(name + " " + roman(i))) i++;
                name = name + " " + roman(i);
            }
        }
        return name;
    }

    private static String roman(int i) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return i < r.length ? r[i] : String.valueOf(i);
    }

    public static String describe(SkillDef d) {
        StringBuilder sb = new StringBuilder();
        String shape = d.shape.phrase();
        sb.append(Character.toUpperCase(shape.charAt(0))).append(shape.substring(1));
        sb.append(": ").append(d.effect.label.toLowerCase(Locale.ROOT)).append(" de ")
                .append(d.element.label.toLowerCase(Locale.ROOT));
        if (d.secondary != null) sb.append(" + ").append(d.secondary.label.toLowerCase(Locale.ROOT));
        sb.append(", ").append(d.trigger.phrase()).append('.');
        return sb.toString();
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
