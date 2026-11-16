package com.socialhub.operations;

import com.socialhub.operations.domain.ExternalOperation;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/**
 * Alta de operaciones externas. Las filas se acumulan en memoria y se guardan por lotes: así se registran también las
 * operaciones que ocurren antes de que la base de datos esté lista y una llamada externa nunca espera a la escritura.
 */
@PrimaryPort
public interface RecordExternalOperations {

    /** Anota una operación terminada; si el registro está lleno, se descarta. */
    void record(ExternalOperation operation);

    /** Guarda lo acumulado. */
    void flush();

    /** Borra lo que pasa del tiempo de conservación. */
    void purge();
}
