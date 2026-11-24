package com.socialhub.brandkit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.KitFixtures;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.InvalidKitException;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitPiece;
import com.socialhub.brandkit.domain.KitStatus;
import com.socialhub.platform.DomainException;
import com.socialhub.platform.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Guardar, aprobar y rechazar kits de marca contra la base de datos real (migración V33 incluida). */
@SpringBootTest(properties = {
        "socialhub.data-dir=target/it/${random.uuid}",
        "socialhub.postgres.port=0",
        "socialhub.postgres.runtime-dir=target/pg-runtime",
        "socialhub.demo.simulate=false",
        "socialhub.ai.initial-delay-millis=3600000",
        "socialhub.ai.dispatch-millis=3600000",
        "socialhub.ai.read-millis=3600000",
        "socialhub.ai.auto-initial-delay-millis=3600000",
        "socialhub.ai.retry-initial-delay-millis=3600000"
})
@AutoConfigureMockMvc
class BrandKitServiceTest {

    @Autowired MockMvc mvc;
    @Autowired BrandKits kits;
    @Autowired KitCodec codec;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    private String brand;
    private static int counter;

    @BeforeEach
    void newBrand() throws Exception {
        String name = "Marca " + (++counter);
        mvc.perform(post("/api/brands").header("X-SocialHub-Request", "1").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\",\"handle\":\"@m" + counter + "\",\"color\":\"#D23A2E\"}"))
                .andExpect(status().isCreated());
        brand = "marca-" + counter;
    }

    private String kitJson() { return codec.write(KitFixtures.delito()); }

    private KitStatus statusOf(BrandKit kit) { return KitStatus.valueOf(jdbc.queryForObject(
            "select status from brand_kit where id = ?", String.class, kit.id())); }

    @Test
    void aValidKitIsSavedAsPendingWithItsVersionAndTask() {
        BrandKit kit = kits.propose(brand, kitJson(), null);
        assertThat(kit.id()).isNotNull();
        assertThat(kit.version()).isEqualTo(1);
        assertThat(kit.status()).isEqualTo(KitStatus.PENDING);
        assertThat(kit.kit().templates()).hasSize(5);
        assertThat(kit.approvedAt()).isNull();
        assertThat(kits.pending(brand)).get().extracting(BrandKit::id).isEqualTo(kit.id());
        assertThat(kits.approved(brand)).isEmpty();
        assertThat(kits.pending(brand).orElseThrow().kit()).isEqualTo(KitFixtures.delito());
    }

    @Test
    void anInvalidKitIsNotSavedAndTheReasonsAreReturned() {
        String bad = codec.write(KitFixtures.with(List.of(KitFixtures.template("cita-4x5", KitPiece.CITA, KitFixtures.cierre(1080, 1350), null))));
        assertThatThrownBy(() -> kits.propose(brand, bad, null)).isInstanceOf(InvalidKitException.class)
                .hasMessageContaining("necesita el campo «campo-cita»");
        assertThatThrownBy(() -> kits.propose(brand, "{}", null)).isInstanceOf(InvalidKitException.class);
        assertThat(kits.pending(brand)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from brand_kit where brand_id = ?", Integer.class, brand)).isZero();
    }

    @Test
    void anUnknownBrandIsRejected() {
        assertThatThrownBy(() -> kits.propose("no-existe", kitJson(), null)).isInstanceOf(NotFoundException.class)
                .hasMessageContaining("No existe la marca");
    }

    @Test
    void approvingMakesItTheOnlyApprovedAndSupersedesThePreviousOne() {
        BrandKit first = kits.propose(brand, kitJson(), null);
        BrandKit approved = kits.approve(first.id());
        assertThat(approved.status()).isEqualTo(KitStatus.APPROVED);
        assertThat(approved.approvedAt()).isNotNull();
        assertThat(kits.approved(brand)).get().extracting(BrandKit::version).isEqualTo(1);
        assertThat(kits.pending(brand)).isEmpty();

        BrandKit second = kits.propose(brand, kitJson(), null);
        assertThat(second.version()).isEqualTo(2);
        assertThat(kits.approved(brand)).get().extracting(BrandKit::version).isEqualTo(1);
        kits.approve(second.id());
        assertThat(kits.approved(brand)).get().extracting(BrandKit::version).isEqualTo(2);
        assertThat(statusOf(first)).isEqualTo(KitStatus.SUPERSEDED);
        assertThat(jdbc.queryForObject("select count(*) from brand_kit where brand_id = ? and status = 'APPROVED'", Integer.class, brand)).isEqualTo(1);
    }

    @Test
    void aNewProposalSupersedesTheOneStillWaiting() {
        BrandKit first = kits.propose(brand, kitJson(), null);
        BrandKit second = kits.propose(brand, kitJson(), null);
        assertThat(statusOf(first)).isEqualTo(KitStatus.SUPERSEDED);
        assertThat(kits.pending(brand)).get().extracting(BrandKit::id).isEqualTo(second.id());
    }

    @Test
    void rejectingNeedsAComment_andKeepsTheApprovedKit() {
        BrandKit approved = kits.approve(kits.propose(brand, kitJson(), null).id());
        BrandKit pending = kits.propose(brand, kitJson(), null);
        assertThatThrownBy(() -> kits.reject(pending.id(), "  ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Escribe qué quieres cambiar");
        assertThatThrownBy(() -> kits.reject(pending.id(), "x".repeat(1001))).isInstanceOf(IllegalArgumentException.class);
        BrandKit rejected = kits.reject(pending.id(), "  Más contraste en el dorado  ");
        assertThat(rejected.status()).isEqualTo(KitStatus.REJECTED);
        assertThat(rejected.comment()).isEqualTo("Más contraste en el dorado");
        assertThat(kits.pending(brand)).isEmpty();
        assertThat(kits.approved(brand)).get().extracting(BrandKit::id).isEqualTo(approved.id());
        // Al pedir otra, la versión sigue contando
        assertThat(kits.propose(brand, kitJson(), null).version()).isEqualTo(3);
    }

    @Test
    void onlyAPendingKitCanBeApprovedOrRejected() {
        BrandKit kit = kits.approve(kits.propose(brand, kitJson(), null).id());
        assertThatThrownBy(() -> kits.approve(kit.id())).isInstanceOf(DomainException.class).hasMessageContaining("Ese kit ya no es el pendiente; recarga.").hasMessageContaining("Este está aprobado");
        assertThatThrownBy(() -> kits.reject(kit.id(), "no")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> kits.approve(-5)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void theDatabaseItselfForbidsTwoApprovedKitsOfABrand() {
        BrandKit one = kits.approve(kits.propose(brand, kitJson(), null).id());
        String insert = "insert into brand_kit (brand_id, version, status, kit_json) values (?, 99, 'APPROVED', '{}')";
        assertThatThrownBy(() -> jdbc.update(insert, brand)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(kits.approved(brand)).get().extracting(BrandKit::id).isEqualTo(one.id());
    }

    @Test
    void kitsOfDifferentBrandsAreIndependent() throws Exception {
        String other = brand;
        newBrand();
        kits.approve(kits.propose(other, kitJson(), null).id());
        assertThat(kits.approved(brand)).isEmpty();
        assertThat(kits.propose(brand, kitJson(), null).version()).isEqualTo(1);
    }

    @Test
    void approvingAKitThatWasSupersededMeanwhileFailsAndLeavesTheApprovedKitInForce() {
        BrandKit approved = kits.approve(kits.propose(brand, kitJson(), null).id());
        BrandKit stale = kits.propose(brand, kitJson(), null);
        TransactionTemplate approving = new TransactionTemplate(transactions);
        TransactionTemplate arriving = new TransactionTemplate(transactions);
        arriving.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Raúl aprueba (en una transacción que ya leyó el kit como pendiente) mientras llega un kit nuevo que lo sustituye
        assertThatThrownBy(() -> approving.executeWithoutResult(status -> {
            assertThat(kits.pending(brand)).get().extracting(BrandKit::id).isEqualTo(stale.id());
            arriving.executeWithoutResult(other -> kits.propose(brand, kitJson(), null));
            kits.approve(stale.id());
        })).isInstanceOf(DomainException.class).hasMessageContaining("recarga");
        // Nada se pisó: el aprobado sigue siéndolo, el visto quedó sustituido y el nuevo espera la decisión
        assertThat(kits.approved(brand)).get().extracting(BrandKit::id).isEqualTo(approved.id());
        assertThat(statusOf(stale)).isEqualTo(KitStatus.SUPERSEDED);
        assertThat(kits.pending(brand)).get().extracting(BrandKit::version).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from brand_kit where brand_id = ? and status = 'APPROVED'", Integer.class, brand)).isEqualTo(1);
    }

    @Test
    void aNewKitArrivingWhileAnotherIsBeingApprovedDoesNotLeaveTheBrandWithoutAnApprovedKit() {
        BrandKit approved = kits.approve(kits.propose(brand, kitJson(), null).id());
        BrandKit pending = kits.propose(brand, kitJson(), null);
        TransactionTemplate proposing = new TransactionTemplate(transactions);
        TransactionTemplate approving = new TransactionTemplate(transactions);
        approving.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Al revés: llega el kit nuevo (tras leer el pendiente) y mientras tanto Raúl aprueba ese pendiente
        proposing.executeWithoutResult(status -> {
            assertThat(kits.pending(brand)).isPresent();
            approving.executeWithoutResult(other -> kits.approve(pending.id()));
            kits.propose(brand, kitJson(), null);
        });
        // Cada uno ve el estado real de la base: el aprobado es el que aprobó Raúl y el nuevo espera su decisión
        assertThat(kits.approved(brand)).get().extracting(BrandKit::id).isEqualTo(pending.id());
        assertThat(statusOf(approved)).isEqualTo(KitStatus.SUPERSEDED);
        assertThat(kits.pending(brand)).get().extracting(BrandKit::version).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from brand_kit where brand_id = ? and status = 'APPROVED'", Integer.class, brand)).isEqualTo(1);
    }

    @Test
    void theMigrationGivesElDelitoTheQuoteAndTheOtherBrandsOnlyTheClosingAndTheStory() throws Exception {
        for (String id : List.of("delito", "el-delito", "litoques", "otra-marca")) {
            jdbc.update("insert into brand (id, name, handle, color) values (?, ?, ?, '#112233')", id, id, "@" + id);
        }
        // Las sentencias «update» de la migración V35, tal cual, sobre marcas que aún tienen el valor por defecto
        var migration = new org.springframework.core.io.ClassPathResource("db/migration/V35__brand_kit_pieces_and_version.sql");
        String sql = new String(migration.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        sql.lines().filter(l -> l.startsWith("update brand")).forEach(jdbc::execute);
        java.util.function.Function<String, List<String>> piecesOf = id -> List.of(jdbc.queryForObject(
                "select array_to_string(kit_pieces, ',') from brand where id = ?", String.class, id).split(","));
        assertThat(piecesOf.apply("delito")).containsExactly("cita", "cierre", "historia");
        assertThat(piecesOf.apply("el-delito")).containsExactly("cita", "cierre", "historia");
        assertThat(piecesOf.apply("litoques")).containsExactly("cierre", "historia");
        assertThat(piecesOf.apply("otra-marca")).containsExactly("cierre", "historia");
        assertThat(piecesOf.apply(brand)).containsExactly("cierre", "historia");
    }
}
