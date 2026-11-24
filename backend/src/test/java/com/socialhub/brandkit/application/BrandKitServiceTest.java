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
        assertThatThrownBy(() -> kits.approve(kit.id())).isInstanceOf(DomainException.class).hasMessageContaining("este está aprobado");
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
}
