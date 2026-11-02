package com.socialhub.production.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record TaskStep(String label, StepState state) {
    TaskStep with(StepState newState) { return new TaskStep(label, newState); }
}
