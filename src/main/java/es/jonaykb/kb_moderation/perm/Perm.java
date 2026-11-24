package es.jonaykb.kb_moderation.perm;

public enum Perm {
    WARN("warn"),
    MUTE("mute"),
    KICK("kick"),
    BAN("ban"),
    HISTORY("history"),
    MODINFO("modinfo"),
    FREEZE("freeze"),
    TELEPORT("teleport"),
    VANISH("vanish"),
    VANISH_SEE("vanish.see"),
    SEE_INVISIBLE("seeinvisible"),
    INVSEE("invsee"),
    INVSEE_EDIT("invsee.edit"),
    STAFFCHAT("staffchat"),
    REPORTS("reports"),
    EXEMPT("exempt");

    public final String key;

    Perm(String key) {
        this.key = key;
    }

    public static Perm byKey(String key) {
        for (Perm p : values()) if (p.key.equals(key)) return p;
        return null;
    }
}
