package com.socialhub;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Sondas de las reglas de capas de {@link ModularityTest}: en {@code archprobes} hay clases que las incumplen a
 * propósito (solo existen en las pruebas), y cada regla tiene que fallar nombrando a su sonda. Si una regla se rompe o se
 * afloja hasta no detectar nada, esta prueba lo dice.
 */
class ArchitectureProbesTest {

    private static final JavaClasses PROBES = new ClassFileImporter().importPackages("archprobes");

    private static void fails(ArchRule rule, String probe) {
        assertThatThrownBy(() -> rule.check(PROBES)).isInstanceOf(AssertionError.class).hasMessageContaining(probe);
    }

    @Test void applicationWithoutTechnology() { fails(ModularityTest.aplicacionSinTecnologia, "AppUsesJpa"); }
    @Test void inboundDoesNotCallOutbound() { fails(ModularityTest.entradaSinSalida, "InAdapter"); }
    @Test void outboundDoesNotKnowInbound() { fails(ModularityTest.salidaSinEntrada, "OutAdapter"); }
    @Test void entitiesOnlyInPersistence() { fails(ModularityTest.entidadesSoloEnPersistencia, "EntityOutsidePersistence"); }
    @Test void springDataOnlyInPersistence() { fails(ModularityTest.repositoriosJpaSoloEnPersistencia, "SpringDataOutsidePersistence"); }
    @Test void secondaryPortsNotInApplication() { fails(ModularityTest.puertosSecundariosFueraDeLaAplicacion, "SecondaryPortInApplication"); }
    @Test void primaryPortsNotInApplication() { fails(ModularityTest.puertosPrimariosEnLaRaiz, "PrimaryPortInApplication"); }
    @Test void transactionsOnlyInApplication() { fails(ModularityTest.transaccionesFueraDeDominioYEntrada, "TransactionalInInbound"); }
    @Test void clocksOnlyInInboundAdapters() { fails(ModularityTest.programacionSoloEnAdaptadoresDeEntrada, "ScheduledInApplication"); }
    @Test void controllersAreThin() { fails(ModularityTest.controladoresFinos, "FatController"); }
    @Test void modulesOnlyUseThePublicApiOfOthers() { fails(ModularityTest.modulosSoloUsanLaApiPublicaDeOtros, "Intruder"); }
    @Test void modulesHaveNoCycles() { fails(ModularityTest.sinCiclos("archprobes.cycle.(*).."), "cycle"); }
    @Test void everyModuleDeclaresApplicationModule() {
        assertThatThrownBy(() -> ModularityTest.declaraSusDependencias("archprobes.undeclared"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("undeclared");
    }
    @Test void aModuleCannotLeaveItsDependenciesOpen() {
        assertThatThrownBy(() -> ModularityTest.declaraSusDependencias("archprobes.open"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("abiertas");
    }
}
