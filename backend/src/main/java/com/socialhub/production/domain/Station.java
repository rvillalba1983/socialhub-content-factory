package com.socialhub.production.domain;

import com.socialhub.platform.DomainException;
import java.util.Objects;
import java.util.Set;

/**
 * Una estación del anillo. Su estado es propio: puede estar operativa con tareas en espera, o bloqueada
 * por un recurso externo (por ejemplo, el PC apagado) aunque sus tareas no hayan fallado.
 */
public final class Station {

    public static final Set<String> STATUSES = Set.of("ok", "run", "wait", "block", "err", "idle", "cancel");

    private final String id;
    private String status;
    private String previousStatus;
    private boolean paused;
    private String note;
    private final int version;

    public Station(String id, String status, String previousStatus, boolean paused, String note, int version) {
        this.id = Objects.requireNonNull(id);
        if (!STATUSES.contains(status)) throw new IllegalArgumentException("Estado de estación desconocido: " + status);
        this.status = status;
        this.previousStatus = previousStatus;
        this.paused = paused;
        this.note = note;
        this.version = version;
    }

    public void pause() {
        if (paused) throw new DomainException("La estación ya está pausada.");
        previousStatus = status;
        status = "wait";
        paused = true;
        note = "Estación pausada por ti.";
    }

    public void resume() {
        if (!paused) throw new DomainException("La estación no está pausada.");
        status = previousStatus == null ? "ok" : previousStatus;
        previousStatus = null;
        paused = false;
        note = null;
    }

    /** Las tareas en cola solo arrancan en estaciones que no estén pausadas ni bloqueadas. */
    public boolean acceptsWork() { return !paused && !"block".equals(status); }

    public String id() { return id; }
    public String status() { return status; }
    public String previousStatus() { return previousStatus; }
    public boolean paused() { return paused; }
    public String note() { return note; }
    public int version() { return version; }
}
