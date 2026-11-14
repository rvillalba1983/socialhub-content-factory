package com.socialhub.publishing;

import static org.assertj.core.api.Assertions.assertThat;

import com.socialhub.promotion.PromotionQueries.QualityRules;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.Publication;
import com.socialhub.publishing.domain.PublicationStatus;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualityCheckerTest {

    @TempDir Path dir;

    private static final QualityRules IDENTITY = new QualityRules(List.of("Compra ya"),
            "Comenta DELITO y te mando el enlace", "#eldelito #poesia");

    private static Publication pub(Platform platform, String type, String caption, String hashtags) {
        return Publication.builder().id("p1").promotionId("promo").platform(platform).type(type).title("Poema")
                .scheduledOn(LocalDate.of(2030, 1, 1)).scheduledTime(LocalTime.NOON).status(PublicationStatus.DRAFT)
                .caption(caption).hashtags(hashtags).build();
    }

    private String image(String name, int w, int h, String format) throws Exception {
        Path file = dir.resolve(name);
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), format, file.toFile());
        return file.toString();
    }

    @Test
    void aGoodPostHasNoIssues() throws Exception {
        var p = pub(Platform.INSTAGRAM, "Carrusel", "Un poema. Comenta delito y te mando el enlace 📩", "#ElDeLito #poesia #libros");
        var issues = QualityChecker.check(p, List.of(image("a.jpg", 1080, 1350, "jpg"), image("b.jpg", 1080, 1080, "jpg")),
                IDENTITY, Map.of("p2", "Otro texto"));
        assertThat(issues).isEmpty();
    }

    @Test
    void textAndIdentityProblems() {
        var p = pub(Platform.INSTAGRAM, "Reel", "¡Cómpra YA el libro!", "#poesiaviva");
        var issues = QualityChecker.check(p, List.of(), IDENTITY, Map.of("p2", "¡Compra ya   el libro!"));
        assertThat(issues).containsKeys("FORBIDDEN:compra ya", "CTA_MISSING", "HASHTAG_MISSING:#eldelito",
                "HASHTAG_MISSING:#poesia", "DUPLICATE:p2", "FILES_MISSING");
        assertThat(issues).doesNotContainKey("TEXT_MISSING");
    }

    @Test
    void storiesDoNotNeedTextButMustBeVertical() throws Exception {
        var p = pub(Platform.INSTAGRAM, "Historia", null, null);
        var issues = QualityChecker.check(p, List.of(image("h.jpg", 1080, 1350, "jpg")), IDENTITY, Map.of());
        assertThat(issues.keySet()).containsExactly("IMAGE_RATIO:" + dir.resolve("h.jpg"));
        assertThat(QualityChecker.check(p, List.of(image("v.jpg", 1080, 1920, "jpg")), IDENTITY, Map.of())).isEmpty();
    }

    @Test
    void platformLimits() throws Exception {
        String longText = "a".repeat(2190) + " Comenta DELITO y te mando el enlace";
        var p = pub(Platform.INSTAGRAM, "Publicación", longText, "#eldelito #poesia");
        String png = image("x.png", 1080, 1080, "png");
        String wide = image("w.jpg", 2000, 500, "jpg");
        var issues = QualityChecker.check(p, List.of(png, wide, dir.resolve("falta.jpg").toString()), IDENTITY, Map.of());
        assertThat(issues).containsKeys("TEXT_TOO_LONG", "FILE_FORMAT:" + png, "IMAGE_RATIO:" + wide,
                "FILE_MISSING:" + dir.resolve("falta.jpg"));
        // En TikTok el PNG sí vale
        var tiktok = pub(Platform.TIKTOK, "Carrusel", "Texto. Comenta DELITO y te mando el enlace", "#eldelito #poesia");
        String v1 = image("t1.png", 1080, 1920, "png");
        String v2 = image("t2.png", 1080, 1920, "png");
        assertThat(QualityChecker.check(tiktok, List.of(v1, v2), IDENTITY, Map.of())).isEmpty();
        Path video = Files.writeString(dir.resolve("clip.mp4"), "x");
        assertThat(QualityChecker.check(tiktok, List.of(v1, video.toString()), IDENTITY, Map.of())).containsKey("CAROUSEL_MIX");
        assertThat(QualityChecker.check(pub(Platform.INSTAGRAM, "Reel", "x Comenta DELITO y te mando el enlace", "#eldelito #poesia"),
                List.of(v1), IDENTITY, Map.of())).containsKey("VIDEO_MISSING");
    }
}
