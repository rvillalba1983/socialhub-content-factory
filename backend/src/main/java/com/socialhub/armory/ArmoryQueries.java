package com.socialhub.armory;

import com.socialhub.armory.domain.ArmoryItem;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Consultas de la armería (solo lectura). */
@PrimaryPort
public interface ArmoryQueries {

    List<ArmoryItem> items();
}
