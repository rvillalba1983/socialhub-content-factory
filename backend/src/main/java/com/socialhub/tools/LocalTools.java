package com.socialhub.tools;

import com.socialhub.tools.domain.ToolResult;
import java.util.List;
import java.util.function.Consumer;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Comprueba las herramientas locales y deja el resultado en la armería del modo real. */
@PrimaryPort
public interface LocalTools {

    /** Tipo de la tarea del motor que ejecuta la comprobación. */
    String TASK_TYPE = "CHECK_LOCAL_TOOLS";

    /** Comprueba cada herramienta, avisa de cada una en {@code log} y devuelve los resultados. */
    List<ToolResult> checkAll(Consumer<String> log) throws InterruptedException;
}
