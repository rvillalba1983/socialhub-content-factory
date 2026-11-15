package com.socialhub.ai;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Las rondas periódicas del orquestador. Cada una trabaja solo en modo real, y un fallo en una ronda se anota y no
 * afecta a las demás.
 */
@PrimaryPort
public interface AiRounds {

    /** Saca hacia Drive los encargos en cola. */
    void dispatchQueued();

    /** Lee los resultados nuevos que ha dejado Claude. */
    void readResults();

    /** Crea los encargos semanales que toquen. */
    void createWeeklyOrders();

    /** Reintenta solos los encargos que han fallado. */
    void retryFailed();

    /** Marca como urgentes los encargos pendientes que ya no llegan a tiempo. */
    void raiseUrgency();
}
