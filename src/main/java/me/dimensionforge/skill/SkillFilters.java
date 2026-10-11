package me.dimensionforge.skill;

import me.dimensionforge.skill.model.Effect;
import me.dimensionforge.skill.model.Element;
import me.dimensionforge.skill.model.Shape;
import me.dimensionforge.skill.model.SkillDef;
import me.dimensionforge.skill.model.Trigger;

import java.util.Comparator;
import java.util.List;

/** Filtros y orden de la biblioteca de habilidades. null = "cualquiera". */
public final class SkillFilters {

    public enum Sort {
        NAME("Nombre"), TIER_DESC("Potencia (mayor primero)"), TIER_ASC("Potencia (menor primero)"),
        COOLDOWN("Enfriamiento (menor primero)"), POWER("Daño/intensidad (mayor primero)");

        public final String label;

        Sort(String label) {
            this.label = label;
        }
    }

    public Effect.Category category;
    public Element element;
    public Trigger trigger;
    public Shape shape;
    public Effect effect;
    public int minTier = 0;
    public boolean signatureOnly = false;
    public Sort sort = Sort.NAME;

    public boolean passes(SkillDef d) {
        if (category != null && d.effect.category != category) {
            return false;
        }
        if (element != null && d.element != element) {
            return false;
        }
        if (trigger != null && d.trigger != trigger) {
            return false;
        }
        if (shape != null && d.shape != shape) {
            return false;
        }
        if (effect != null && d.effect != effect && d.secondary != effect) {
            return false;
        }
        if (d.tier < minTier) {
            return false;
        }
        return !signatureOnly || d.signature;
    }

    public Comparator<SkillDef> comparator() {
        return switch (sort) {
            case NAME -> Comparator.comparing((SkillDef d) -> d.name.toLowerCase());
            case TIER_DESC -> Comparator.comparingInt((SkillDef d) -> -d.tier).thenComparing(d -> d.name.toLowerCase());
            case TIER_ASC -> Comparator.comparingInt((SkillDef d) -> d.tier).thenComparing(d -> d.name.toLowerCase());
            case COOLDOWN -> Comparator.comparingDouble((SkillDef d) -> d.cooldown).thenComparing(d -> d.name.toLowerCase());
            case POWER -> Comparator.comparingDouble((SkillDef d) -> -d.power).thenComparing(d -> d.name.toLowerCase());
        };
    }

    public void sort(List<SkillDef> list) {
        list.sort(comparator());
    }

    public boolean any() {
        return category != null || element != null || trigger != null || shape != null || effect != null
                || minTier > 0 || signatureOnly;
    }

    public void reset() {
        category = null;
        element = null;
        trigger = null;
        shape = null;
        effect = null;
        minTier = 0;
        signatureOnly = false;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        if (category != null) sb.append("Cat: ").append(category.label).append("  ");
        if (element != null) sb.append("Elem: ").append(element.label).append("  ");
        if (trigger != null) sb.append("Disp: ").append(trigger.label).append("  ");
        if (shape != null) sb.append("Forma: ").append(shape.label).append("  ");
        if (effect != null) sb.append("Efecto: ").append(effect.label).append("  ");
        if (minTier > 0) sb.append("Nivel≥").append(minTier).append("  ");
        if (signatureOnly) sb.append("Solo firma");
        return sb.length() == 0 ? "Sin filtros" : sb.toString().trim();
    }
}
