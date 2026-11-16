package com.socialhub;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Set;
import org.jmolecules.archunit.JMoleculesArchitectureRules;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.modulith.docs.Documenter;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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

    // Reglas de capas de la hexagonal en todos los módulos (domain / application / adapter.in / adapter.out). Cada una tiene
    // su sonda en ArchitectureProbesTest: una clase que la incumple a propósito y comprueba que la regla falla.

    /** La aplicación orquesta con puertos: no sabe de persistencia, de la web ni de los adaptadores. */
    @ArchTest
    static final ArchRule aplicacionSinTecnologia = noClasses().that().resideInAPackage("..application..").and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "jakarta.persistence..", "org.hibernate..", "org.springframework.data..", "org.springframework.web..",
                    "jakarta.servlet..", "com.socialhub.api..", "com.socialhub..adapter..")
            .because("la aplicación habla con el dominio y con los puertos, no con JPA, la web ni los adaptadores");

    /** Los adaptadores de entrada (web, programación, arranque) no llaman a los de salida: pasan por la aplicación. */
    @ArchTest
    static final ArchRule entradaSinSalida = noClasses().that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .because("un adaptador de entrada llama a un puerto primario, nunca directamente a la persistencia o a los ficheros");

    /** Los adaptadores de salida no conocen a los de entrada. */
    @ArchTest
    static final ArchRule salidaSinEntrada = noClasses().that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..")
            .because("la dependencia va de fuera hacia dentro, nunca entre adaptadores");

    /** Las entidades JPA viven solo en la persistencia (y en la revisión de auditoría de la plataforma). */
    @ArchTest
    static final ArchRule entidadesSoloEnPersistencia = classes().that().areAnnotatedWith("jakarta.persistence.Entity")
            .or().areAnnotatedWith("jakarta.persistence.Embeddable").or().areAnnotatedWith("jakarta.persistence.MappedSuperclass")
            .should().resideInAnyPackage("..adapter.out.persistence..", "com.socialhub.platform")
            .because("el modelo de tablas es un detalle del adaptador de salida; el dominio tiene su propio modelo");

    /** Los repositorios de Spring Data solo en la persistencia. */
    @ArchTest
    static final ArchRule repositoriosJpaSoloEnPersistencia = classes().that().areAssignableTo(org.springframework.data.repository.Repository.class)
            .should().resideInAPackage("..adapter.out.persistence..")
            .because("el resto del código usa los puertos del dominio, no Spring Data");

    /** Los puertos secundarios los define el dominio; los adaptadores de salida los implementan. */
    @ArchTest
    static final ArchRule puertosSecundariosFueraDeLaAplicacion = noClasses().that().areAnnotatedWith(org.jmolecules.architecture.hexagonal.SecondaryPort.class)
            .should().resideInAnyPackage("..application..", "..adapter..")
            .because("un puerto de salida que vive en la aplicación o en un adaptador invierte la dependencia: va en el dominio");

    /** Los puertos primarios son la cara pública del módulo: ni en el dominio, ni en la aplicación, ni en un adaptador. */
    @ArchTest
    static final ArchRule puertosPrimariosEnLaRaiz = noClasses().that().areAnnotatedWith(org.jmolecules.architecture.hexagonal.PrimaryPort.class)
            .should().resideInAnyPackage("..domain..", "..application..", "..adapter..")
            .because("el puerto primario es lo que otros módulos y los adaptadores de entrada ven; se implementa en application");

    /** Las transacciones las abre la aplicación: ni el dominio ni los adaptadores de entrada. */
    @ArchTest
    static final ArchRule transaccionesFueraDeDominioYEntrada = noClasses()
            .that().resideInAnyPackage("..domain..", "..adapter.in..").and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.transaction.annotation..",
                    "org.springframework.transaction.support..", "jakarta.transaction..")
            .because("la unidad de trabajo es de la aplicación; un controlador o un reloj que abre transacciones lleva lógica que no es suya "
                    + "(escuchar un evento tras la confirmación, con TransactionalEventListener, no abre ninguna)");

    /** Los relojes ({@code @Scheduled}) son adaptadores de entrada: solo llaman a un puerto primario. */
    @ArchTest
    static final ArchRule programacionSoloEnAdaptadoresDeEntrada = methods().that().areAnnotatedWith(Scheduled.class)
            .should().beDeclaredInClassesThat().resideInAnyPackage("..adapter.in..")
            .because("quien decide cuándo toca una ronda no es la aplicación ni el dominio");

    // Reglas de fronteras entre módulos y de los adaptadores web. Cada una tiene su sonda en ArchitectureProbesTest.

    /** Un controlador no abre la puerta de atrás: solo habla con puertos primarios, no con repositorios, entidades ni la aplicación. */
    @ArchTest
    static final ArchRule controladoresFinos = noClasses().that().resideInAPackage("..adapter.in..").and().doNotHaveSimpleName("package-info")
            .should().dependOnClassesThat().areAnnotatedWith(org.jmolecules.architecture.hexagonal.SecondaryPort.class)
            .orShould().dependOnClassesThat().resideInAnyPackage("..application..", "org.springframework.data..", "jakarta.persistence..")
            .orShould().dependOnClassesThat().areAnnotatedWith("jakarta.persistence.Entity")
            .because("un adaptador de entrada llama a un puerto primario (la raíz del módulo), nunca a un repositorio, una entidad ni a la aplicación");

    /**
     * De otro módulo solo se usa su API pública: la raíz (puertos primarios y sus tipos), los eventos de dominio
     * ({@code domain.events}), el dominio de IA (interfaz con nombre {@code ai::domain}) y, desde un adaptador web, las vistas de la
     * API de los demás módulos ({@code adapter.in.web}, interfaz con nombre {@code web}). Nunca sus repositorios, entidades,
     * aplicación ni adaptadores. {@code platform} y {@code api} son compartidos.
     */
    @ArchTest
    static final ArchRule modulosSoloUsanLaApiPublicaDeOtros = classes()
            .should(soloUsarLaApiPublicaDeOtrosModulos())
            .because("el dominio, la aplicación y los adaptadores de un módulo son internos: otro módulo usa sus puertos primarios o sus eventos");

    /** Sin ciclos entre módulos (Spring Modulith ya lo comprueba en {@code verify()}; esta regla lo hace explícito y tiene sonda). */
    @ArchTest
    static final ArchRule sinCiclosEntreModulos = sinCiclos("com.socialhub.(*)..");

    static ArchRule sinCiclos(String pattern) {
        return slices().matching(pattern).should().beFreeOfCycles();
    }

    private static final Set<String> SHARED_MODULES = Set.of("platform", "api");

    /** El módulo de una clase: el primer paquete tras {@code com.socialhub.} (o tras {@code archprobes.} en las sondas); vacío si no tiene. */
    static String moduleOf(JavaClass type) {
        String name = type.getPackageName();
        String rest = name.startsWith("com.socialhub.") ? name.substring("com.socialhub.".length())
                : name.startsWith("archprobes.") ? name.substring("archprobes.".length()) : null;
        if (rest == null || rest.isEmpty()) return "";
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static String insideModule(JavaClass type) {
        String module = moduleOf(type);
        String name = type.getPackageName();
        int at = name.indexOf("." + module);
        String rest = at < 0 ? "" : name.substring(at + 1 + module.length());
        return rest.startsWith(".") ? rest.substring(1) : rest;
    }

    private static ArchCondition<JavaClass> soloUsarLaApiPublicaDeOtrosModulos() {
        return new ArchCondition<>("usar de otros módulos solo su raíz, sus eventos, su interfaz web o el dominio de IA") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String from = moduleOf(origin);
                if (from.isEmpty()) return;
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass();
                    String to = moduleOf(target);
                    if (to.isEmpty() || to.equals(from) || SHARED_MODULES.contains(to)) continue;
                    String inside = insideModule(target);
                    boolean root = inside.isEmpty();
                    boolean events_ = inside.equals("domain.events");
                    boolean aiDomain = to.equals("ai") && inside.equals("domain");
                    boolean webView = inside.equals("adapter.in.web") && origin.getPackageName().contains(".adapter.in.web");
                    if (!(root || events_ || aiDomain || webView)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

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
