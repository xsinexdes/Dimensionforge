package me.dimensionforge.skill.model;

/** Forma / objetivo de una habilidad. */
public enum Shape {
    SELF("Sí mismo", Kind.SELF, "Aura", true, "Manto", false),
    TARGET_PLAYER("Jugador objetivo", Kind.TARGET, "Marca", true, "Sentencia", true),
    NEAREST_PLAYER("Jugador más cercano", Kind.TARGET, "Zarpazo", false, "Acoso", false),
    ALL_IN_RADIUS("Todos en radio", Kind.AREA, "Estallido", false, "Nova", true),
    ALLIES("Aliados", Kind.ALLIES, "Plegaria", true, "Estandarte", false),
    PROJECTILE_STRAIGHT("Proyectil recto", Kind.PROJECTILE, "Proyectil", false, "Saeta", true),
    PROJECTILE_HOMING("Proyectil teledirigido", Kind.PROJECTILE, "Orbe", false, "Espectro", false),
    BEAM_LINE("Rayo / línea", Kind.LINE, "Rayo", false, "Haz", false),
    CONE("Cono", Kind.AREA, "Aliento", false, "Ráfaga", true),
    CIRCLE_WAVE("Círculo / onda", Kind.AREA, "Onda", true, "Pulso", false),
    RING("Anillo", Kind.AREA, "Anillo", false, "Corona", true),
    SKY_RAIN("Lluvia desde el cielo", Kind.GROUND, "Lluvia", true, "Tormenta", true),
    GROUND_UNDER_TARGET("Suelo bajo el objetivo", Kind.GROUND, "Grieta", true, "Trampa", true),
    METEORS("Meteoritos", Kind.GROUND, "Meteoro", false, "Cometa", false),
    SUMMON_CIRCLE("Invocar en círculo", Kind.SUMMON, "Círculo", false, "Ritual", false),
    LEAP("Salto", Kind.MOVEMENT, "Salto", false, "Aterrizaje", false),
    CHARGE("Embestida", Kind.MOVEMENT, "Embestida", true, "Carga", true),
    TELEPORT_BEHIND("Teletransporte detrás del objetivo", Kind.MOVEMENT, "Paso", false, "Emboscada", true);

    public enum Kind { SELF, TARGET, AREA, ALLIES, PROJECTILE, LINE, GROUND, SUMMON, MOVEMENT }

    public final String label;
    public final Kind kind;
    public final String noun1;
    public final boolean fem1;
    public final String noun2;
    public final boolean fem2;

    Shape(String label, Kind kind, String noun1, boolean fem1, String noun2, boolean fem2) {
        this.label = label;
        this.kind = kind;
        this.noun1 = noun1;
        this.fem1 = fem1;
        this.noun2 = noun2;
        this.fem2 = fem2;
    }

    public String phrase() {
        return switch (this) {
            case SELF -> "sobre sí mismo";
            case TARGET_PLAYER -> "sobre su objetivo";
            case NEAREST_PLAYER -> "sobre el jugador más cercano";
            case ALL_IN_RADIUS -> "sobre todos los jugadores a su alrededor";
            case ALLIES -> "sobre sus aliados";
            case PROJECTILE_STRAIGHT -> "con un proyectil recto";
            case PROJECTILE_HOMING -> "con un proyectil teledirigido";
            case BEAM_LINE -> "con un rayo en línea";
            case CONE -> "en un cono frente a él";
            case CIRCLE_WAVE -> "con una onda expansiva";
            case RING -> "en un anillo a su alrededor";
            case SKY_RAIN -> "con una lluvia desde el cielo";
            case GROUND_UNDER_TARGET -> "bajo los pies de su objetivo";
            case METEORS -> "con meteoritos";
            case SUMMON_CIRCLE -> "en un círculo de invocación";
            case LEAP -> "tras un gran salto";
            case CHARGE -> "con una embestida";
            case TELEPORT_BEHIND -> "teletransportándose tras su objetivo";
        };
    }
}
