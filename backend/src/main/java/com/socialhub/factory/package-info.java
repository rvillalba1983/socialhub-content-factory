/**
 * Vista completa del anillo ({@org.springframework.modulith.ApplicationModule(allowedDependencies = {"promotion", "promotion::web", "production", "production::web", "publishing", "publishing::web", "armory", "armory::web", "ai", "backup", "pieces", "platform", "api"})
@code GET /api/factory}): compone, en una sola transacción de lectura, lo que
 * exponen los demás módulos a través de sus interfaces {@code web}.
 */
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
@org.jspecify.annotations.NullMarked
package com.socialhub.factory;
