package com.socialhub.armory.domain;

import java.util.List;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface ArmoryRepository {
    List<ArmoryItem> findAll();
}
