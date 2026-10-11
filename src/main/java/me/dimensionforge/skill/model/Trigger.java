package me.dimensionforge.skill.model;

/** Cuándo se activa una habilidad. */
public enum Trigger {
    INTERVAL("Cada X segundos", 8.0, "s"),
    SEE_PLAYER("Al ver a un jugador", 0, ""),
    ON_ATTACK("Al atacar", 0, ""),
    ON_DAMAGED("Al recibir daño", 0, ""),
    HEALTH_BELOW("Vida por debajo de X%", 50, "%"),
    HEALTH_ABOVE("Vida por encima de X%", 80, "%"),
    ON_DEATH("Al morir", 0, ""),
    ON_SPAWN("Al aparecer", 0, ""),
    IDLE("Al estar quieto X segundos", 5, "s"),
    PLAYER_NEAR("Jugador cerca (X bloques)", 8, "bloques"),
    PLAYER_FAR("Jugador lejos (X bloques)", 16, "bloques"),
    HIT_BY_WEAPON("Golpeado por un tipo de arma", 0, ""),
    ON_CRIT("Al recibir un golpe crítico", 0, "");

    public final String label;
    public final double defaultValue;
    public final String unit;

    Trigger(String label, double defaultValue, String unit) {
        this.label = label;
        this.defaultValue = defaultValue;
        this.unit = unit;
    }

    /** Texto para la descripción automática. */
    public String phrase() {
        return switch (this) {
            case INTERVAL -> "cada cierto tiempo";
            case SEE_PLAYER -> "al ver a un jugador";
            case ON_ATTACK -> "al atacar";
            case ON_DAMAGED -> "al recibir daño";
            case HEALTH_BELOW -> "con la vida baja";
            case HEALTH_ABOVE -> "con la vida alta";
            case ON_DEATH -> "al morir";
            case ON_SPAWN -> "al aparecer";
            case IDLE -> "tras quedarse quieto";
            case PLAYER_NEAR -> "cuando un jugador se acerca";
            case PLAYER_FAR -> "cuando los jugadores se alejan";
            case HIT_BY_WEAPON -> "al ser golpeado por cierta arma";
            case ON_CRIT -> "al recibir un golpe crítico";
        };
    }

    /** Adjetivo corto usado para desempatar nombres. */
    public String qualifier() {
        return switch (this) {
            case INTERVAL -> "Periódico";
            case SEE_PLAYER -> "Vigilante";
            case ON_ATTACK -> "Vengativo";
            case ON_DAMAGED -> "Reactivo";
            case HEALTH_BELOW -> "Desesperado";
            case HEALTH_ABOVE -> "Soberbio";
            case ON_DEATH -> "Póstumo";
            case ON_SPAWN -> "Naciente";
            case IDLE -> "Latente";
            case PLAYER_NEAR -> "Centinela";
            case PLAYER_FAR -> "Acechante";
            case HIT_BY_WEAPON -> "Rencoroso";
            case ON_CRIT -> "Brutal";
        };
    }
}
