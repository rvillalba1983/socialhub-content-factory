package com.socialhub.promotion.domain;

import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.Identity;

@AggregateRoot
public record Brand(@Identity String id, String name, String handle, String color, boolean demo) {}
