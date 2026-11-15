package com.socialhub.promotion.domain;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/** Una decisión editorial de una marca («nada de precios», «no más de dos reels por semana»…). */
@ValueObject
public record EditorialDecision(long id, String text, Instant createdAt) {}
