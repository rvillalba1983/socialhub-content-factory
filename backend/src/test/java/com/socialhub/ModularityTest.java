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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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

    // Los package-info quedan fuera: llevan la anotación @NamedInterface de Modulith en domain.events (único hueco público del dominio).

    /** El dominio no sabe nada de frameworks, de persistencia, de la web ni de los adaptadores. */
    @ArchTest
    static final ArchRule dominioSinFrameworks = noClasses().that().resideInAPackage("..domain..").and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "org.hibernate..", "jakarta.servlet..",
                    "com.fasterxml.jackson..", "tools.jackson..", "com.socialhub.api..", "com.socialhub..adapter..")
            .because("el dominio es Java puro: sin Spring, JPA, web, JSON ni adaptadores");

    /** El dominio solo mira al dominio (y a la plataforma común), además de Java y las anotaciones de jMolecules. */
    @ArchTest
    static final ArchRule dominioSoloMiraAlDominio = classes().that().resideInAPackage("..domain..").and().doNotHaveSimpleName("package-info")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..domain..", "com.socialhub.platform..", "java..", "javax..", "org.jmolecules..", "org.jspecify..",
                    "lombok..")
            .because("el dominio no depende de la raíz de su módulo ni de otros módulos");

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules modules = ApplicationModules.of(SocialHubApplication.class);
        modules.verify();
        new Documenter(modules).writeModulesAsPlantUml();
    }
}
