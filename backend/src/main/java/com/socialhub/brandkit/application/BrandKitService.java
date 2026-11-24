package com.socialhub.brandkit.application;

import com.socialhub.brandkit.BrandKits;
import com.socialhub.brandkit.domain.BrandKit;
import com.socialhub.brandkit.domain.BrandKitRepository;
import com.socialhub.brandkit.domain.InvalidKitException;
import com.socialhub.brandkit.domain.KitCodec;
import com.socialhub.brandkit.domain.KitContent;
import com.socialhub.brandkit.domain.KitStatus;
import com.socialhub.brandkit.domain.KitValidator;
import com.socialhub.brandkit.domain.TemplateChecker;
import com.socialhub.platform.DomainException;
import com.socialhub.platform.NotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class BrandKitService implements BrandKits {

    static final int MAX_COMMENT = 1000;

    private final BrandKitRepository kits;
    private final KitCodec codec;
    private final KitValidator validator;
    private final Clock clock;

    BrandKitService(BrandKitRepository kits, KitCodec codec, TemplateChecker checker, Clock clock) {
        this.codec = codec;
        this.kits = kits;
        this.validator = new KitValidator(checker);
        this.clock = clock;
    }

    @Override
    public BrandKit propose(String brandId, String kitJson, @Nullable String sourceTaskId) {
        if (!kits.brandExists(brandId)) {
            throw new NotFoundException("No existe la marca «" + brandId + "».");
        }
        KitContent content = codec.read(kitJson);
        List<String> errors = validator.validate(content);
        if (!errors.isEmpty()) throw new InvalidKitException(errors);
        // Un kit nuevo sustituye al que seguía esperando la decisión de Raúl
        kits.findByStatus(brandId, KitStatus.PENDING)
                .ifPresent(old -> kits.save(old.withStatus(KitStatus.SUPERSEDED, old.comment(), null)));
        return kits.save(new BrandKit(null, brandId, kits.nextVersion(brandId), KitStatus.PENDING, content,
                codec.write(content), null, sourceTaskId, Instant.now(clock), null));
    }

    @Override
    public BrandKit approve(long id) {
        BrandKit kit = pendingKit(id, "aprobar");
        kits.findByStatus(kit.brandId(), KitStatus.APPROVED)
                .ifPresent(old -> kits.save(old.withStatus(KitStatus.SUPERSEDED, old.comment(), old.approvedAt())));
        return kits.save(kit.withStatus(KitStatus.APPROVED, kit.comment(), Instant.now(clock)));
    }

    @Override
    public BrandKit reject(long id, String comment) {
        String text = comment.strip();
        if (text.isEmpty()) throw new IllegalArgumentException("Escribe qué quieres cambiar para pedir otra versión.");
        if (text.length() > MAX_COMMENT) throw new IllegalArgumentException("El comentario supera los " + MAX_COMMENT + " caracteres.");
        BrandKit kit = pendingKit(id, "rechazar");
        return kits.save(kit.withStatus(KitStatus.REJECTED, text, null));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandKit> approved(String brandId) { return kits.findByStatus(brandId, KitStatus.APPROVED); }

    @Override
    @Transactional(readOnly = true)
    public Optional<BrandKit> pending(String brandId) { return kits.findByStatus(brandId, KitStatus.PENDING); }

    private BrandKit pendingKit(long id, String action) {
        BrandKit kit = kits.find(id).orElseThrow(() -> new NotFoundException("No existe el kit " + id + "."));
        if (kit.status() != KitStatus.PENDING) {
            throw new DomainException("Solo se puede " + action + " un kit pendiente; este está " + describe(kit.status()) + ".");
        }
        return kit;
    }

    private static String describe(KitStatus status) {
        return switch (status) {
            case PENDING -> "pendiente";
            case APPROVED -> "aprobado";
            case SUPERSEDED -> "sustituido por otro";
            case REJECTED -> "rechazado";
        };
    }
}
