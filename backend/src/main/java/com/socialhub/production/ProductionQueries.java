package com.socialhub.production;

import com.socialhub.production.domain.ProductionOverview;
import com.socialhub.production.domain.Station;
import com.socialhub.production.domain.StationSummary;
import com.socialhub.production.domain.Task;
import com.socialhub.production.domain.TaskFilter;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Consultas de producción (solo lectura). */
@PrimaryPort
public interface ProductionQueries {

    Task task(String id);

    List<Task> tasks(TaskFilter filter);

    List<Station> stations();

    /** Las estaciones con su recuento de tareas y todas las tareas. */
    ProductionOverview overview();

    /** Una estación con el recuento de sus tareas. */
    StationSummary station(String stationId);

    /**
     * Lo que necesita atención de Raúl en lo ya leído: tareas con error o bloqueadas y estaciones bloqueadas o caídas.
     * No consulta nada: así la vista completa lee la producción una sola vez.
     */
    List<ProductionAlert> alertsOf(ProductionOverview overview);
}
