package com.socialhub.factory.domain;

import java.util.List;

/** Lo que necesita la atención de Raúl: los avisos y las recomendaciones de «Qué hacer ahora». */
public record Attention(List<FactoryAlert> alerts, List<Recommendation> recommendations) {}
