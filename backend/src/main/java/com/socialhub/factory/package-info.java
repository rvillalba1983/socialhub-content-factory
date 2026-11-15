/**
 * Vista completa del anillo ({@code GET /api/factory}): compone, en una sola transacción de lectura, lo que
 * exponen los demás módulos a través de sus interfaces {@code web}.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"promotion", "promotion::web", "production", "production::web", "publishing", "publishing::web", "armory", "armory::web", "ai", "ai::domain", "backup", "pieces", "platform", "api"})
@org.jmolecules.architecture.hexagonal.PrimaryAdapter
@org.jspecify.annotations.NullMarked
package com.socialhub.factory;
