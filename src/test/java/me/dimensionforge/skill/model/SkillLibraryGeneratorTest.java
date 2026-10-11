package me.dimensionforge.skill.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Comprueba que la biblioteca generada cumple los mínimos pedidos (sin Bukkit). */
class SkillLibraryGeneratorTest {

    private static List<SkillDef> all() {
        List<SkillDef> list = new ArrayList<>(SkillLibraryGenerator.generate());
        list.addAll(SignatureSkills.all());
        return list;
    }

    @Test
    void hasAtLeastOneThousandSkills() {
        assertTrue(all().size() >= 1000, "habilidades: " + all().size());
    }

    @Test
    void idsAndNamesAreUnique() {
        Set<String> ids = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (SkillDef d : all()) {
            assertTrue(ids.add(d.id), "id repetido: " + d.id);
            assertTrue(names.add(d.name), "nombre repetido: " + d.name);
            assertFalse(d.id.isBlank());
            assertFalse(d.name.isBlank());
            assertFalse(d.description.isBlank());
        }
    }

    @Test
    void coversTheVariety() {
        Set<Trigger> triggers = EnumSet.noneOf(Trigger.class);
        Set<Shape> shapes = EnumSet.noneOf(Shape.class);
        Set<Effect> effects = EnumSet.noneOf(Effect.class);
        Set<Element> elements = EnumSet.noneOf(Element.class);
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        for (SkillDef d : all()) {
            triggers.add(d.trigger);
            shapes.add(d.shape);
            effects.add(d.effect);
            elements.add(d.element);
            modifiers.addAll(d.modifiers);
        }
        assertTrue(triggers.size() >= 12, "disparadores: " + triggers.size());
        assertTrue(shapes.size() >= 15, "formas: " + shapes.size());
        assertTrue(effects.size() >= 40, "efectos: " + effects.size());
        assertTrue(elements.size() >= 10, "elementos: " + elements.size());
        assertTrue(modifiers.size() >= 10, "modificadores: " + modifiers.size());
    }

    @Test
    void generationIsDeterministic() {
        List<SkillDef> a = SkillLibraryGenerator.generate();
        List<SkillDef> b = SkillLibraryGenerator.generate();
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).id, b.get(i).id);
            assertEquals(a.get(i).toMap(), b.get(i).toMap());
        }
    }

    @Test
    void mapRoundTripKeepsValues() {
        for (SkillDef d : all()) {
            SkillDef back = SkillDef.fromMap(d.id, d.toMap());
            assertEquals(d.trigger, back.trigger);
            assertEquals(d.shape, back.shape);
            assertEquals(d.effect, back.effect);
            assertEquals(d.element, back.element);
            // toMap redondea a 2 decimales al guardar
            assertEquals(d.power, back.power, 0.006);
            assertEquals(d.name, back.name);
        }
    }

    @Test
    void everySkillUsesACompatibleShapeAndEffect() {
        for (SkillDef d : SkillLibraryGenerator.generate()) {
            assertTrue(SkillLibraryGenerator.compatible(d.shape, d.effect), d.id);
            assertTrue(SkillLibraryGenerator.allowedTriggers(d.shape).contains(d.trigger), d.id);
        }
    }
}
