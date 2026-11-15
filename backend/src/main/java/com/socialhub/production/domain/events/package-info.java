/**
 * Eventos de dominio de producción. Son la parte de {@code domain} que otros módulos pueden escuchar
 * (por ejemplo, publicaciones atiende «revisión aprobada»); el resto del dominio sigue siendo interno.
 */
@org.springframework.modulith.NamedInterface("events")
@org.jspecify.annotations.NullMarked
package com.socialhub.production.domain.events;
