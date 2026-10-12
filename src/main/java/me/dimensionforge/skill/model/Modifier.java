package me.dimensionforge.skill.model;

/** Modificadores que ajustan los números base de una habilidad generada (12). */
public enum Modifier {
    EMPOWERED("Potenciada", "Furia", 1.35, 1.0, 1.15, 1.0, 1.0),
    SWIFT("Rápida", "Presteza", 0.9, 1.0, 0.6, 0.9, 1.0),
    WIDE("Amplia", "Amplitud", 0.9, 1.5, 1.1, 1.0, 1.0),
    LINGERING("Persistente", "Persistencia", 0.85, 1.0, 1.0, 1.8, 1.0),
    MULTI("Múltiple", "Multitud", 0.7, 1.0, 1.2, 1.0, 2.5),
    FOCUSED("Precisa", "Precisión", 1.2, 0.7, 0.9, 1.0, 1.0),
    SAVAGE("Salvaje", "Salvajismo", 1.6, 0.9, 1.5, 0.8, 1.0),
    ENDURING("Duradera", "Aguante", 1.0, 1.0, 1.3, 1.5, 1.0),
    VOLATILE("Volátil", "Volatilidad", 1.25, 1.25, 0.9, 0.7, 1.5),
    RELENTLESS("Implacable", "Insistencia", 0.8, 1.0, 0.5, 1.0, 1.0),
    MINOR("Menor", "Menor", 0.6, 0.8, 0.5, 0.8, 1.0),
    GREATER("Mayor", "Mayor", 1.8, 1.4, 1.8, 1.4, 1.3);

    public final String label;
    /** Sustantivo usado al final del nombre. */
    public final String tag;
    public final double powerMul;
    public final double radiusMul;
    public final double cooldownMul;
    public final double durationMul;
    public final double countMul;

    Modifier(String label, String tag, double powerMul, double radiusMul, double cooldownMul, double durationMul,
             double countMul) {
        this.label = label;
        this.tag = tag;
        this.powerMul = powerMul;
        this.radiusMul = radiusMul;
        this.cooldownMul = cooldownMul;
        this.durationMul = durationMul;
        this.countMul = countMul;
    }
}
