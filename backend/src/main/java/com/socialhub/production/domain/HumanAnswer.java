package com.socialhub.production.domain;

import java.time.Instant;
import org.jmolecules.ddd.annotation.ValueObject;

/** Lo que Claude preguntó a Raúl en un encargo y lo que Raúl contestó. */
@ValueObject
public record HumanAnswer(String question, String answer, Instant at) {}
