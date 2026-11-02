package com.socialhub.armory.domain;

import java.util.List;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

/** Un recurso de la armería. {@code kind}: AGENT, TOOL, SERVICE o DEVICE; {@code status}: nivel de estado. */
@AggregateRoot
public record ArmoryItem(@Identity String id, String kind, String name, String description, String status,
                         String note, List<String> capabilities, boolean demo) {}
