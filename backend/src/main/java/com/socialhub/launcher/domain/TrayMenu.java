package com.socialhub.launcher.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * El menú del icono de la bandeja. Es solo el modelo (qué hay y en qué orden): pintarlo es cosa del adaptador.
 * Doble clic en el icono = {@link Action#OPEN}.
 */
public final class TrayMenu {

    public enum Action { OPEN, OPEN_LOGS, TOGGLE_AUTOSTART, QUIT }

    /** Una línea del menú: acción pulsable, interruptor, texto sin pulsar o separador. */
    public record Entry(Kind kind, String label, boolean checked, Action action) {
        public enum Kind { ACTION, CHECK, TEXT, SEPARATOR }

        static Entry action(String label, Action action) { return new Entry(Kind.ACTION, label, false, action); }
        static Entry check(String label, boolean checked, Action action) { return new Entry(Kind.CHECK, label, checked, action); }
        static Entry text(String label) { return new Entry(Kind.TEXT, label, false, null); }
        static Entry separator() { return new Entry(Kind.SEPARATOR, "", false, null); }
    }

    public static final String TOOLTIP = "SocialHub";

    private TrayMenu() {}

    /**
     * @param version la versión instalada (se enseña como texto, no pulsable)
     * @param autostartAvailable este sistema permite abrirlo al iniciar sesión: si no, no sale el interruptor
     * @param autostartOn está activado ahora
     */
    public static List<Entry> entries(String version, boolean autostartAvailable, boolean autostartOn) {
        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.text("SocialHub " + version));
        entries.add(Entry.separator());
        entries.add(Entry.action("Abrir SocialHub", Action.OPEN));
        entries.add(Entry.action("Abrir carpeta de registros", Action.OPEN_LOGS));
        if (autostartAvailable) entries.add(Entry.check("Abrir al iniciar sesión", autostartOn, Action.TOGGLE_AUTOSTART));
        entries.add(Entry.separator());
        entries.add(Entry.action("Cerrar SocialHub", Action.QUIT));
        return List.copyOf(entries);
    }
}
