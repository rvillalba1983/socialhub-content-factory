package com.socialhub.production.domain;

import com.socialhub.platform.DomainException;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;
import org.jspecify.annotations.Nullable;

/**
 * Una estación del anillo. Su estado es propio: puede estar operativa con tareas en espera, o bloqueada
 * por un recurso externo (por ejemplo, el PC apagado) aunque sus tareas no hayan fallado.
 */
@AggregateRoot
@Getter
public final class Station {

    @Identity
    private final String id;
    private final int position;
    private final String name;
    private final String description;
    private StatusLevel status;
    private @Nullable StatusLevel previousStatus;
    private boolean paused;
    private @Nullable String note;
    private final String limitNote;
    private final List<String> tools;
    private final long version;

    @Builder
    public Station(String id, int position, String name, @Nullable String description, StatusLevel status,
                   @Nullable StatusLevel previousStatus, boolean paused, @Nullable String note,
                   @Nullable String limitNote, @Nullable List<String> tools, long version) {
        this.id = Objects.requireNonNull(id);
        this.position = position;
        this.name = Objects.requireNonNull(name);
        this.description = description == null ? "" : description;
        this.status = Objects.requireNonNull(status);
        this.previousStatus = previousStatus;
        this.paused = paused;
        this.note = note;
        this.limitNote = limitNote == null ? "" : limitNote;
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.version = version;
    }

    public void pause() {
        if (paused) throw new DomainException("La estación ya está pausada.");
        previousStatus = status;
        status = StatusLevel.WAITING;
        paused = true;
        note = "Estación pausada por ti.";
    }

    public void resume() {
        if (!paused) throw new DomainException("La estación no está pausada.");
        status = previousStatus == null ? StatusLevel.OK : previousStatus;
        previousStatus = null;
        paused = false;
        note = null;
    }

    public List<StationCommandType> availableCommands() {
        return List.of(paused ? StationCommandType.RESUME : StationCommandType.PAUSE);
    }

    /** Las tareas en cola solo arrancan en estaciones que no estén pausadas ni bloqueadas. */
    public boolean acceptsWork() { return !paused && status != StatusLevel.BLOCKED; }
}
