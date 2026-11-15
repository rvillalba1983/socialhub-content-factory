package com.socialhub.ai;

import com.socialhub.ai.domain.AiRequestPage;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Consulta de los envíos a Claude. */
@PrimaryPort
public interface AiRequests {

    /** Página de envíos, los más recientes primero. */
    AiRequestPage page(int offset, int limit);
}
