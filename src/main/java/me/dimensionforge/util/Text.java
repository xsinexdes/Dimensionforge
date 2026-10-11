package me.dimensionforge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/** Utilidades de texto con MiniMessage. */
public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {
    }

    /** Convierte un texto MiniMessage a componente (sin cursiva por defecto, útil para ítems). */
    public static Component mm(String raw) {
        if (raw == null) {
            return Component.empty();
        }
        return MM.deserialize(raw).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> mm(List<String> raw) {
        List<Component> out = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) {
                out.add(mm(s));
            }
        }
        return out;
    }

    public static String plain(Component c) {
        return c == null ? "" : PlainTextComponentSerializer.plainText().serialize(c);
    }

    /** Escapa etiquetas MiniMessage de un texto introducido por un usuario. */
    public static String escape(String s) {
        return s == null ? "" : MM.escapeTags(s);
    }
}
