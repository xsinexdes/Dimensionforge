package me.dimensionforge.skill.model;

import java.util.ArrayList;
import java.util.List;

/** Habilidades de firma hechas a mano (jefes y mobs con personalidad). Ids con prefijo "sig_". */
public final class SignatureSkills {
    private SignatureSkills() {
    }

    private static SkillDef sig(String id, String name, String desc, Trigger tr, double trv, Shape shape, Element el,
                                Effect ef, Effect sec, double power, double radius, double range, double cd,
                                double dur, int count, int tier, String extra) {
        SkillDef d = new SkillDef();
        d.id = id;
        d.name = name;
        d.description = desc;
        d.trigger = tr;
        d.triggerValue = trv;
        d.shape = shape;
        d.element = el;
        d.effect = ef;
        d.secondary = sec;
        d.power = power;
        d.radius = radius;
        d.range = range;
        d.cooldown = cd;
        d.duration = dur;
        d.count = count;
        d.tier = tier;
        d.extra = extra == null ? "" : extra;
        d.signature = true;
        d.chance = tr == Trigger.ON_DAMAGED || tr == Trigger.ON_ATTACK || tr == Trigger.ON_CRIT
                || tr == Trigger.HIT_BY_WEAPON ? 0.4 : 1.0;
        return d;
    }

    public static List<SkillDef> all() {
        List<SkillDef> l = new ArrayList<>();
        Trigger I = Trigger.INTERVAL;
        l.add(sig("sig_lava_breath", "Aliento de Magma", "Un cono de fuego que quema y empuja a quien lo respira.", I, 9, Shape.CONE, Element.FIRE, Effect.DAMAGE, Effect.BURN, 8, 6, 10, 9, 5, 1, 3, null));
        l.add(sig("sig_ember_nova", "Nova de Brasas", "Una explosión de brasas alrededor del jefe sin romper bloques.", I, 12, Shape.CIRCLE_WAVE, Element.FIRE, Effect.EXPLOSION_NO_BLOCKS, Effect.KNOCKBACK, 9, 7, 0, 14, 3, 1, 4, null));
        l.add(sig("sig_meteor_storm", "Tormenta de Meteoros", "Meteoritos de fuego caen sobre la zona de los jugadores.", Trigger.HEALTH_BELOW, 50, Shape.METEORS, Element.FIRE, Effect.EXPLOSION_NO_BLOCKS, null, 7, 3.5, 18, 20, 3, 6, 5, null));
        l.add(sig("sig_frost_prison", "Prisión de Escarcha", "Congela al objetivo y lo encierra en hielo temporal.", I, 14, Shape.GROUND_UNDER_TARGET, Element.ICE, Effect.TEMP_ICE, Effect.FREEZE, 3, 3, 20, 16, 6, 1, 3, null));
        l.add(sig("sig_blizzard", "Ventisca Mortal", "Lluvia de escarcha que ralentiza a todos en la zona.", Trigger.HEALTH_BELOW, 60, Shape.SKY_RAIN, Element.ICE, Effect.SLOW, Effect.DAMAGE, 3, 7, 0, 18, 6, 10, 4, null));
        l.add(sig("sig_chain_bolt", "Rayo Encadenado", "Un relámpago cae sobre el jugador más cercano y lo aturde.", I, 10, Shape.NEAREST_PLAYER, Element.LIGHTNING, Effect.LIGHTNING, Effect.STUN, 9, 3, 16, 10, 2, 1, 4, null));
        l.add(sig("sig_storm_call", "Llamada de la Tormenta", "Varios relámpagos caen en el radio del jefe.", Trigger.HEALTH_BELOW, 40, Shape.SKY_RAIN, Element.LIGHTNING, Effect.LIGHTNING, null, 8, 8, 0, 20, 3, 7, 5, null));
        l.add(sig("sig_toxic_cloud", "Nube Tóxica", "Envenena a todos los jugadores cercanos.", Trigger.PLAYER_NEAR, 6, Shape.ALL_IN_RADIUS, Element.POISON, Effect.POISON, Effect.NAUSEA, 3, 6, 0, 15, 6, 1, 2, null));
        l.add(sig("sig_spider_brood", "Cría Venenosa", "Invoca arañas pequeñas alrededor del jefe.", Trigger.HEALTH_BELOW, 70, Shape.SUMMON_CIRCLE, Element.POISON, Effect.SWARM, null, 3, 4, 0, 35, 25, 6, 3, "CAVE_SPIDER"));
        l.add(sig("sig_web_trap", "Trampa de Telarañas", "Cubre de telarañas temporales al objetivo.", Trigger.SEE_PLAYER, 0, Shape.GROUND_UNDER_TARGET, Element.POISON, Effect.TEMP_COBWEB, Effect.SLOW, 3, 3, 18, 14, 7, 1, 2, null));
        l.add(sig("sig_shadow_step", "Paso Sombrío", "Se teletransporta tras el objetivo y lo deja a oscuras.", I, 11, Shape.TELEPORT_BEHIND, Element.SHADOW, Effect.DAMAGE, Effect.DARKNESS, 6, 3, 20, 12, 5, 1, 3, null));
        l.add(sig("sig_umbral_clones", "Ilusiones Umbrías", "Crea clones temporales que confunden a los jugadores.", Trigger.HEALTH_BELOW, 55, Shape.SELF, Element.SHADOW, Effect.CLONES, null, 2, 3, 0, 40, 20, 2, 4, "VEX"));
        l.add(sig("sig_void_pull", "Vórtice del Vacío", "Atrae a los jugadores hacia el jefe.", I, 16, Shape.CIRCLE_WAVE, Element.SHADOW, Effect.PULL_TO_POINT, Effect.WEAKNESS, 3, 9, 0, 18, 4, 1, 3, null));
        l.add(sig("sig_holy_beam", "Rayo Sagrado", "Un haz de luz que revela y daña a quien lo recibe.", I, 8, Shape.BEAM_LINE, Element.LIGHT, Effect.DAMAGE, Effect.GLOW, 8, 1.5, 20, 9, 5, 1, 3, null));
        l.add(sig("sig_radiant_ward", "Égida Radiante", "El jefe se envuelve en un escudo de luz.", Trigger.HEALTH_BELOW, 50, Shape.SELF, Element.LIGHT, Effect.SHIELD, Effect.REGENERATION, 6, 3, 0, 40, 10, 1, 4, null));
        l.add(sig("sig_earth_spikes", "Púas de Tierra", "Grietas bajo los pies que lanzan al aire y dañan.", I, 8, Shape.GROUND_UNDER_TARGET, Element.EARTH, Effect.LAUNCH_UP, Effect.DAMAGE, 6, 3, 20, 9, 3, 1, 3, null));
        l.add(sig("sig_stone_skin", "Piel de Piedra", "Gana resistencia temporal al recibir daño.", Trigger.ON_DAMAGED, 0, Shape.SELF, Element.EARTH, Effect.RESISTANCE, null, 3, 3, 0, 20, 8, 1, 3, null));
        l.add(sig("sig_quake", "Terremoto", "Una onda de choque que derriba a los jugadores cercanos.", I, 15, Shape.CIRCLE_WAVE, Element.EARTH, Effect.SHOCKWAVE, Effect.STUN, 7, 8, 0, 16, 2, 1, 4, null));
        l.add(sig("sig_gale", "Ráfaga Huracanada", "Empuja lejos a todos los que están cerca.", Trigger.PLAYER_NEAR, 4, Shape.CIRCLE_WAVE, Element.WIND, Effect.KNOCKBACK, null, 5, 6, 0, 12, 3, 1, 2, null));
        l.add(sig("sig_sky_lift", "Ascenso del Viento", "Lanza al aire al jugador objetivo.", I, 13, Shape.TARGET_PLAYER, Element.WIND, Effect.LAUNCH_UP, Effect.LEVITATE, 4, 3, 18, 14, 3, 1, 2, null));
        l.add(sig("sig_blood_frenzy", "Frenesí de Sangre", "Gana fuerza y velocidad al estar herido.", Trigger.HEALTH_BELOW, 40, Shape.SELF, Element.BLOOD, Effect.STRENGTH, Effect.SPEED, 4, 3, 0, 45, 12, 1, 4, null));
        l.add(sig("sig_crimson_leech", "Sanguijuela Carmesí", "Roba vida al jugador más cercano.", I, 10, Shape.NEAREST_PLAYER, Element.BLOOD, Effect.LIFESTEAL, Effect.BLEED, 6, 3, 10, 11, 4, 1, 3, null));
        l.add(sig("sig_hemorrhage", "Hemorragia", "Sangrado prolongado al golpear.", Trigger.ON_ATTACK, 0, Shape.TARGET_PLAYER, Element.BLOOD, Effect.BLEED, null, 3, 3, 6, 10, 6, 1, 2, null));
        l.add(sig("sig_arcane_orb", "Orbe Arcano", "Un orbe teledirigido que marca al objetivo.", I, 9, Shape.PROJECTILE_HOMING, Element.ARCANE, Effect.DAMAGE, Effect.MARK, 6, 3, 22, 9, 6, 1, 3, null));
        l.add(sig("sig_arcane_volley", "Salva Arcana", "Una lluvia de proyectiles arcanos.", Trigger.HEALTH_BELOW, 60, Shape.SKY_RAIN, Element.ARCANE, Effect.DAMAGE, null, 4, 6, 0, 16, 3, 9, 4, null));
        l.add(sig("sig_silence_hex", "Maleficio del Silencio", "Silencia y desarma al objetivo.", I, 18, Shape.TARGET_PLAYER, Element.ARCANE, Effect.SILENCE, Effect.DISARM, 3, 3, 20, 20, 4, 1, 3, null));
        l.add(sig("sig_soul_drain", "Drenaje de Almas", "Drena experiencia a los jugadores cercanos.", I, 14, Shape.ALL_IN_RADIUS, Element.ARCANE, Effect.DRAIN_XP, Effect.WITHER, 4, 7, 0, 18, 5, 1, 3, null));
        l.add(sig("sig_rending_charge", "Embestida Desgarradora", "Carga contra el objetivo y lo empuja.", Trigger.SEE_PLAYER, 0, Shape.CHARGE, Element.EARTH, Effect.DAMAGE, Effect.KNOCKBACK, 7, 2.5, 14, 11, 3, 1, 3, null));
        l.add(sig("sig_titan_leap", "Salto del Titán", "Salta sobre los jugadores y aterriza con una sacudida.", Trigger.PLAYER_FAR, 12, Shape.LEAP, Element.EARTH, Effect.SHOCKWAVE, Effect.SLOW, 8, 5, 14, 12, 3, 1, 4, null));
        l.add(sig("sig_death_burst", "Estallido Final", "Explota al morir sin romper bloques.", Trigger.ON_DEATH, 0, Shape.CIRCLE_WAVE, Element.FIRE, Effect.EXPLOSION_NO_BLOCKS, Effect.BURN, 8, 5, 0, 5, 3, 1, 3, null));
        l.add(sig("sig_death_swarm", "Último Aliento", "Libera un enjambre al morir.", Trigger.ON_DEATH, 0, Shape.SUMMON_CIRCLE, Element.SHADOW, Effect.SWARM, null, 3, 3, 0, 5, 15, 5, 3, "SILVERFISH"));
        l.add(sig("sig_spawn_roar", "Rugido de Aparición", "Un rugido que asusta al aparecer.", Trigger.ON_SPAWN, 0, Shape.CIRCLE_WAVE, Element.SHADOW, Effect.SCARE, Effect.NAUSEA, 3, 8, 0, 5, 3, 1, 2, null));
        l.add(sig("sig_legion", "Llamada de la Legión", "Invoca esbirros que combaten por el jefe.", Trigger.HEALTH_BELOW, 66, Shape.SUMMON_CIRCLE, Element.BLOOD, Effect.SUMMON_MOBS, null, 3, 4, 0, 40, 30, 4, 4, "ZOMBIE"));
        l.add(sig("sig_skeleton_guard", "Guardia Esquelética", "Invoca esqueletos arqueros.", Trigger.HEALTH_BELOW, 40, Shape.SUMMON_CIRCLE, Element.LIGHT, Effect.SUMMON_MOBS, null, 3, 4, 0, 40, 30, 3, 4, "SKELETON"));
        l.add(sig("sig_rage_pulse", "Pulso de Ira", "Se fortalece con cada golpe crítico recibido.", Trigger.ON_CRIT, 0, Shape.SELF, Element.FIRE, Effect.STRENGTH, Effect.ABSORPTION, 3, 3, 0, 20, 8, 1, 3, null));
        l.add(sig("sig_iron_counter", "Contragolpe de Hierro", "Refleja el daño recibido de armas pesadas.", Trigger.HIT_BY_WEAPON, 0, Shape.SELF, Element.EARTH, Effect.REFLECT_DAMAGE, null, 40, 3, 0, 15, 4, 1, 3, null));
        l.add(sig("sig_frost_aura", "Aura Gélida", "Ralentiza a quien se acerca demasiado.", Trigger.PLAYER_NEAR, 5, Shape.RING, Element.ICE, Effect.SLOW, Effect.FREEZE, 3, 5, 0, 10, 4, 1, 2, null));
        l.add(sig("sig_mending_prayer", "Plegaria Restauradora", "Cura a los aliados cercanos.", I, 14, Shape.ALLIES, Element.LIGHT, Effect.HEAL, Effect.REGENERATION, 8, 12, 0, 16, 6, 1, 3, null));
        l.add(sig("sig_ward_banner", "Estandarte de Guerra", "Refuerza a los aliados con fuerza.", Trigger.SEE_PLAYER, 0, Shape.ALLIES, Element.BLOOD, Effect.STRENGTH, Effect.SPEED, 3, 12, 0, 30, 10, 1, 3, null));
        l.add(sig("sig_dark_title", "Presagio Oscuro", "Un título amenazador y oscuridad para el jugador.", Trigger.ON_SPAWN, 0, Shape.NEAREST_PLAYER, Element.SHADOW, Effect.TITLE, Effect.DARKNESS, 1, 3, 20, 5, 4, 1, 1, "<dark_red><bold>¡HAS DESPERTADO ALGO!"));
        l.add(sig("sig_rusting_touch", "Toque Oxidante", "Daña la durabilidad del arma del jugador.", Trigger.ON_ATTACK, 0, Shape.TARGET_PLAYER, Element.POISON, Effect.DURABILITY_DAMAGE, Effect.HUNGER, 5, 3, 6, 12, 3, 1, 2, null));
        l.add(sig("sig_phase_roll", "Giro de Fase", "Animación de fase y velocidad al cambiar de etapa.", Trigger.HEALTH_BELOW, 50, Shape.SELF, Element.ARCANE, Effect.SPEED, Effect.MODEL_ANIMATION, 3, 3, 0, 60, 8, 1, 2, "attack"));
        l.add(sig("sig_mine_collapse", "Derrumbe de la Mina", "Lluvia de piedras que daña y ralentiza (jefe de las minas).", Trigger.HEALTH_BELOW, 60, Shape.SKY_RAIN, Element.EARTH, Effect.DAMAGE, Effect.SLOW, 5, 6, 0, 16, 3, 10, 4, null));
        l.add(sig("sig_mine_dynamite", "Dinamita", "Meteoritos explosivos sin romper bloques (jefe de las minas).", Trigger.HEALTH_BELOW, 35, Shape.METEORS, Element.FIRE, Effect.EXPLOSION_NO_BLOCKS, Effect.KNOCKBACK, 8, 3.5, 18, 18, 3, 6, 5, null));
        l.add(sig("sig_dust_cloud", "Nube de Polvo", "Ciega al objetivo con polvo de la mina.", I, 12, Shape.ALL_IN_RADIUS, Element.EARTH, Effect.BLIND, null, 3, 6, 0, 16, 4, 1, 2, null));
        return l;
    }
}
