package com.socialhub.production.domain;

import org.jmolecules.ddd.annotation.ValueObject;

@ValueObject
public record TaskCheck(String label, boolean passed) {}
