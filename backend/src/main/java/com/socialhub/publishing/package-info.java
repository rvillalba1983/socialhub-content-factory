/** Publicaciones de cada promoción, con su ciclo de vida. Nunca pasan a «publicada» sin confirmación externa. */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"ai", "promotion", "armory", "production", "production::events", "platform", "platform::live", "platform::observability", "api"})
@org.jspecify.annotations.NullMarked
package com.socialhub.publishing;
