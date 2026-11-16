package com.socialhub.production;

import com.socialhub.production.domain.AiModel;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationCommandType;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskCommandType;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** Órdenes que Raúl da desde la interfaz sobre las tareas y las estaciones. */
@PrimaryPort
public interface ProductionOrders {

    /** Ejecuta una orden sobre una tarea. Lanza DomainException si no está permitida. */
    Task execute(String taskId, TaskCommandType command, @Nullable AiModel model);

    /** Pausa o reanuda una estación (y con ella lo que tiene en curso). */
    Station execute(String stationId, StationCommandType command);
}
