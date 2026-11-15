package com.socialhub;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Set;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Comprueba la arquitectura:
 * <ul>
 *   <li>Spring Modulith: cada módulo solo usa la parte pública de los demás.</li>
 *   <li>jMolecules: reglas de DDD (dentro de {@code verify()}) y de la arquitectura hexagonal.</li>
 *   <li>ArchUnit: el dominio sin frameworks y que solo mira al dominio, y reglas generales de código.</li>
 * </ul>
 */
@AnalyzeClasses(packages = "com.socialhub", importOptions = ImportOption.DoNotIncludeTests.class)
class ModularityTest {

    // Las reglas de DDD de jMolecules (JMoleculesDddRules.all()) ya las ejecuta modules.verify(): con una sonda (un
    // agregado que referencia a otro) falla modulesRespectTheirBoundaries, así que no se repiten aquí.

    // La hexagonal se mantiene a propósito: no se ha comprobado que verify() la cubra igual de bien.
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

    // Reglas generales de código

    @ArchTest
    static final ArchRule sinSalidaEstandar = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS;

    @ArchTest
    static final ArchRule sinInyeccionPorCampo = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;

    @ArchTest
    static final ArchRule sinJavaUtilLogging = noClasses().should().dependOnClassesThat().resideInAPackage("java.util.logging..")
            .because("el registro va por SLF4J (@Slf4j); la regla GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING "
                    + "no detectó una llamada a Logger.getLogger en la sonda, esta sí");

    private static final Set<String> TIME_TYPES = Set.of("java.time.Instant", "java.time.LocalDate",
            "java.time.LocalDateTime", "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.time.LocalTime");

    /** Toda la hora sale del {@code Clock} (que las pruebas fijan): nadie pide la hora del sistema sin pasarle uno. */
    @ArchTest
    static final ArchRule horaSoloDelClock = noClasses()
            .that().doNotHaveFullyQualifiedName("com.socialhub.platform.ClockConfiguration")
            .should().callMethodWhere(new DescribedPredicate<JavaMethodCall>("sea now() sin argumentos de un tipo de fecha") {
                @Override
                public boolean test(JavaMethodCall call) {
                    return call.getTarget().getName().equals("now") && call.getTarget().getRawParameterTypes().isEmpty()
                            && TIME_TYPES.contains(call.getTargetOwner().getFullName());
                }
            })
            .because("sin Clock inyectado, las pruebas no pueden fijar la hora");

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules modules = ApplicationModules.of(SocialHubApplication.class);
        modules.verify();
    }

    /** Escribe la documentación de los módulos (diagramas y fichas) en {@code target/spring-modulith-docs}. */
    @Test
    void writesModuleDocumentation() {
        new Documenter(ApplicationModules.of(SocialHubApplication.class)).writeDocumentation();
    }
}
