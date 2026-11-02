package com.socialhub;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.jmolecules.archunit.JMoleculesDddRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Comprueba la arquitectura:
 * <ul>
 *   <li>Spring Modulith: cada módulo solo usa la parte pública de los demás.</li>
 *   <li>jMolecules: reglas de DDD (agregados, entidades, objetos valor) y de la arquitectura hexagonal.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.socialhub", importOptions = ImportOption.DoNotIncludeTests.class)
class ModularityTest {

    @ArchTest
    static final ArchRule ddd = JMoleculesDddRules.all();

    @ArchTest
    static final ArchRule hexagonal = JMoleculesArchitectureRules.ensureHexagonal();

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules modules = ApplicationModules.of(SocialHubApplication.class);
        modules.verify();
        new Documenter(modules).writeModulesAsPlantUml();
    }
}
