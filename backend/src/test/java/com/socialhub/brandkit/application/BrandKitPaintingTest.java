package com.socialhub.brandkit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.socialhub.brandkit.KitFixtures;
import com.socialhub.brandkit.KitTemplateChecker;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.BrandKitRepository;
import com.socialhub.brandkit.domain.InvalidKitException;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitContent;
import com.socialhub.brandkit.domain.KitStatus;
import com.socialhub.brandkit.domain.TemplatePreview;
import com.socialhub.brandkit.domain.TemplatePreviewer;
import com.socialhub.platform.DomainException;
import com.socialhub.production.ProductionAiTasks;
import com.socialhub.promotion.PromotionQueries;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Un kit cuya plantilla no se pinta no se propone ni se aprueba; las vistas previas se pintan una sola vez. */
class BrandKitPaintingTest {

    /** Códec de mentira: el «JSON» es una clave que remite al contenido guardado. */
    private static final class FakeCodec implements KitCodec {
        private final Map<String, KitContent> store = new HashMap<>();

        @Override public KitContent read(String json) {
            KitContent kit = store.get(json);
            if (kit == null) throw new InvalidKitException(List.of("El kit no es un JSON válido."));
            return kit;
        }

        @Override public String write(KitContent kit) {
            String key = "kit-" + store.size();
            store.put(key, kit);
            return key;
        }
    }

    private static final class MemoryRepository implements BrandKitRepository {
        private final Map<Long, BrandKit> kits = new HashMap<>();
        private long next = 1;

        @Override public BrandKit save(BrandKit kit) {
            BrandKit stored = kit.id() == null
                    ? new BrandKit(next++, kit.brandId(), kit.version(), kit.status(), kit.kit(), kit.kitJson(), kit.comment(),
                            kit.sourceTaskId(), kit.createdAt(), kit.approvedAt())
                    : kit;
            kits.put(stored.id(), stored);
            return stored;
        }

        @Override public Optional<BrandKit> find(long id) { return Optional.ofNullable(kits.get(id)); }

        @Override public Optional<BrandKit> findByStatus(String brandId, KitStatus status) {
            return kits.values().stream().filter(k -> k.brandId().equals(brandId) && k.status() == status).findFirst();
        }

        @Override public boolean brandExists(String brandId) { return true; }

        @Override public int nextVersion(String brandId) { return kits.size() + 1; }
    }

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final FakeCodec codec = new FakeCodec();
    private final MemoryRepository repository = new MemoryRepository();
    private final TemplatePreviewer previewer = mock(TemplatePreviewer.class);
    private final BrandKitService service = new BrandKitService(repository, codec, new KitTemplateChecker(), previewer,
            Clock.fixed(Instant.parse("2026-11-24T20:00:00Z"), ZoneOffset.UTC), mock(ProductionAiTasks.class), mock(PromotionQueries.class));

    private void paints() {
        when(previewer.preview(anyString(), anyInt())).thenReturn(new TemplatePreviewer.Result(PNG, List.of(), List.of()));
    }

    private void failsToPaint(String svgStart) {
        when(previewer.preview(org.mockito.ArgumentMatchers.startsWith(svgStart), anyInt()))
                .thenReturn(new TemplatePreviewer.Result(null, List.of(), List.of("El texto no cabe en la caja «titulo».")));
    }

    @Test
    void aKitWithATemplateThatDoesNotPaintIsNotProposed() {
        paints();
        // Solo falla la plantilla de historia: es la única con 1920 de alto y campo «cta»
        when(previewer.preview(org.mockito.ArgumentMatchers.contains("campo-cta"), anyInt()))
                .thenReturn(new TemplatePreviewer.Result(null, List.of(), List.of("El texto no cabe en la caja «cta».")));
        String json = codec.write(KitFixtures.delito());
        assertThatThrownBy(() -> service.propose("b", json, null)).isInstanceOf(InvalidKitException.class)
                .hasMessageContaining("La plantilla «historia-9x16» no se pinta").hasMessageContaining("no cabe en la caja");
        assertThat(service.check(json)).singleElement().asString().contains("historia-9x16");
        assertThat(repository.kits).isEmpty();
    }

    @Test
    void aTemplateWithoutPngAndWithoutErrorsDoesNotPaintEither() {
        when(previewer.preview(anyString(), anyInt())).thenReturn(new TemplatePreviewer.Result(null, List.of(), List.of()));
        String json = codec.write(KitFixtures.delito());
        assertThat(service.check(json)).hasSize(5).allSatisfy(m -> assertThat(m).contains("no se pudo generar la vista previa"));
    }

    @Test
    void approvingChecksThePaintingAgainAndKeepsTheKitPending() {
        paints();
        BrandKit pending = service.propose("b", codec.write(KitFixtures.delito()), null);
        assertThat(pending.status()).isEqualTo(KitStatus.PENDING);
        // Otro kit llega ya guardado (por ejemplo, de una versión anterior del motor) con una plantilla que ahora no se pinta
        BrandKit stored = repository.save(new BrandKit(null, "b", 2, KitStatus.PENDING,
                KitFixtures.with(List.of(KitFixtures.template("cierre-4x5", com.socialhub.brandkit.domain.KitPiece.CIERRE,
                        KitFixtures.cierre(1080, 1350).replace("TÍTULO", "OTRO TÍTULO"), new com.socialhub.brandkit.domain.ImageArea(0, 0, 1080, 700)))),
                "{}", null, null, Instant.now(), null));
        failsToPaint("<svg");
        assertThatThrownBy(() -> service.approve(stored.id())).isInstanceOf(DomainException.class)
                .hasMessageContaining("No se puede aprobar").hasMessageContaining("«cierre-4x5» no se pinta");
        assertThat(repository.find(stored.id()).orElseThrow().status()).isEqualTo(KitStatus.PENDING);
    }

    @Test
    void thePreviewsAreNotPaintedAgainOnEveryReload() {
        paints();
        BrandKit kit = service.propose("b", codec.write(KitFixtures.delito()), null);
        verify(previewer, times(5)).preview(anyString(), anyInt());
        for (int i = 0; i < 3; i++) {
            List<TemplatePreview> previews = service.previews(kit);
            assertThat(previews).hasSize(5).allSatisfy(p -> assertThat(p.pngBase64()).isNotNull());
        }
        // Ni al recargar la ficha ni al aprobar se pinta otra vez
        service.approve(kit.id());
        verify(previewer, times(5)).preview(anyString(), anyInt());
        verify(previewer, atLeastOnce()).preview(anyString(), anyInt());
    }
}
