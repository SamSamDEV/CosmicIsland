package fr.cosmicisland;

public enum KLRole {
    PARTICIPANT,
    SPECTATEUR,
    ORGANISATEUR;

    public String id() {
        return name().toLowerCase();
    }

    public static KLRole parse(String text) {
        for (KLRole role : values()) {
            if (role.name().equalsIgnoreCase(text)) {
                return role;
            }
        }
        return null;
    }
}
