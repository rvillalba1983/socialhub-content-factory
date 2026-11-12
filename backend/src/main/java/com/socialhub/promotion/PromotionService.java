package com.socialhub.promotion;

import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.NotFoundException;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.domain.Brand;
import com.socialhub.promotion.domain.BrandDraft;
import com.socialhub.promotion.domain.FolderAssignment;
import com.socialhub.promotion.domain.FolderRepository;
import com.socialhub.promotion.domain.FolderRole;
import com.socialhub.promotion.domain.Promotion;
import com.socialhub.promotion.domain.PromotionDraft;
import com.socialhub.promotion.domain.PromotionRepository;
import com.socialhub.promotion.domain.Slugs;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta y edición de marcas y promociones. Lo creado en modo demo queda marcado como ejemplo. */
@PrimaryPort
@Service
@RequiredArgsConstructor
@Transactional
public class PromotionService {

    private final PromotionRepository repository;
    private final AppModeHolder mode;
    private final ApplicationEventPublisher events;
    private final FolderRepository folders;
    private final PromotionQueries queries;
    private final com.socialhub.promotion.domain.IdentityRepository identities;
    private final com.socialhub.promotion.domain.DecisionRepository decisions;
    private final java.time.Clock clock;
    private final com.socialhub.promotion.domain.FolderMaker folderMaker;

    public Brand createBrand(BrandDraft draft) {
        String id = Slugs.of(draft.name(), repository::brandIdTaken);
        Brand brand = repository.save(new Brand(id, draft.name().strip(), draft.handle().strip(), draft.color(), mode.isDemo()));
        events.publishEvent(new FactoryChanged("brand", id));
        return brand;
    }

    public Brand updateBrand(String id, BrandDraft draft) {
        Brand current = repository.brand(id).orElseThrow(() -> new NotFoundException("No existe la marca " + id));
        Brand brand = repository.save(new Brand(id, draft.name().strip(), draft.handle().strip(), draft.color(), current.demo()));
        events.publishEvent(new FactoryChanged("brand", id));
        return brand;
    }

    public Promotion createPromotion(PromotionDraft draft) {
        validate(draft);
        String id = Slugs.of(draft.name(), repository::promotionIdTaken);
        Promotion promotion = repository.save(draft.toPromotion(id, mode.isDemo()));
        events.publishEvent(new FactoryChanged("promotion", id));
        return promotion;
    }

    public Promotion updatePromotion(String id, PromotionDraft draft) {
        Promotion current = repository.promotion(id).orElseThrow(() -> new NotFoundException("No existe la promoción " + id));
        validate(draft);
        Promotion promotion = repository.save(draft.toPromotion(id, current.demo()));
        events.publishEvent(new FactoryChanged("promotion", id));
        return promotion;
    }

    /**
     * Las promociones planificadas que ya han empezado (empiezan hoy o antes y no han terminado) pasan a activas.
     * @return los identificadores de las promociones activadas
     */
    public List<String> activateStarted(java.time.LocalDate today) {
        List<String> activated = new java.util.ArrayList<>();
        for (Promotion p : repository.promotions()) {
            if (!"PLANNED".equals(p.status()) || p.startsOn().isAfter(today) || p.endsOn().isBefore(today)) continue;
            // Sin método «copia con otro estado» en el registro: MapStruct lo tomaría por un setter
            repository.save(new Promotion(p.id(), p.brandId(), p.name(), p.description(), p.scope(), p.cadence(),
                    p.startsOn(), p.endsOn(), "ACTIVE", p.metricsSource(), p.goals(), p.rules(), p.humanTodos(),
                    p.milestones(), p.resources(), p.automations(), p.routes(), p.settings(), p.demo()));
            events.publishEvent(new FactoryChanged("promotion", p.id()));
            activated.add(p.id());
        }
        return activated;
    }

    private void validate(PromotionDraft draft) {
        if (repository.brand(draft.brandId()).isEmpty()) {
            throw new IllegalArgumentException("No existe la marca " + draft.brandId());
        }
        if (draft.endsOn().isBefore(draft.startsOn())) {
            throw new IllegalArgumentException("La promoción no puede terminar antes de empezar");
        }
    }

    /** Sustituye los papeles de carpeta de la marca. Las promociones los heredan. */
    public List<FolderAssignment> replaceBrandFolders(String brandId, Map<FolderRole, String> assignments) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        folders.replaceBrandFolders(brandId, clean(assignments));
        events.publishEvent(new FactoryChanged("brand", brandId));
        return queries.brandFolders(brandId);
    }

    /** Subcarpetas de intercambio con Claude dentro de «Encargos» (y la pública de Instagram). */
    static final List<String> ORDER_SUBFOLDERS = List.of("para-claude", "para-claude-pc", "de-claude", "publico");

    /**
     * Carpetas de la marca. Con carpeta raíz: Material es la propia raíz, el resto son subcarpetas suyas en minúsculas
     * (borradores, generados, aprobados, publicados, informes, encargos) y dentro de encargos se crean para-claude,
     * para-claude-pc, de-claude y publico. Los 7 papeles pasan a apuntar ahí. Sin raíz se crean las carpetas ya asignadas.
     * Nunca se borra ni se mueve nada: lo que ya exista se respeta.
     */
    public List<FolderAssignment> createBrandFolders(String brandId, @org.jspecify.annotations.Nullable String base) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        Map<FolderRole, String> current = new EnumMap<>(FolderRole.class);
        current.putAll(folders.brandFolders(brandId));
        boolean fromRoot = base != null && !base.isBlank();
        if (fromRoot) {
            String root = clean(Map.of(FolderRole.MATERIAL, base)).get(FolderRole.MATERIAL);
            if (!folderMaker.reachable(root)) {
                throw new com.socialhub.platform.DomainException("No se puede acceder a la carpeta raíz «" + root
                        + "». ¿Está la unidad disponible y Google Drive para ordenador abierto?");
            }
            current.clear();
            for (FolderRole role : FolderRole.values()) {
                current.put(role, role == FolderRole.MATERIAL ? root : Path.of(root).resolve(role.label().toLowerCase(java.util.Locale.ROOT)).toString());
            }
        }
        if (current.isEmpty()) {
            throw new com.socialhub.platform.DomainException("Escribe una carpeta raíz o la ruta de cada papel antes de crear las carpetas.");
        }
        Map<FolderRole, String> assigned = clean(current);
        java.util.List<String> failed = new java.util.ArrayList<>();
        for (Map.Entry<FolderRole, String> f : assigned.entrySet()) {
            try {
                folderMaker.ensure(f.getValue());
            } catch (RuntimeException e) {
                failed.add(f.getKey().label() + " (" + e.getMessage() + ")");
            }
        }
        String orders = assigned.get(FolderRole.ORDERS);
        if (fromRoot && orders != null) {
            for (String sub : ORDER_SUBFOLDERS) {
                try {
                    folderMaker.ensure(Path.of(orders).resolve(sub).toString());
                } catch (RuntimeException e) {
                    failed.add(sub + " (" + e.getMessage() + ")");
                }
            }
        }
        folders.replaceBrandFolders(brandId, assigned);
        events.publishEvent(new FactoryChanged("brand", brandId));
        if (!failed.isEmpty()) {
            throw new com.socialhub.platform.DomainException("No se pudieron crear: " + String.join("; ", failed)
                    + ". ¿Está abierto Google Drive para ordenador?");
        }
        return queries.brandFolders(brandId);
    }

    /** Sustituye las excepciones de la promoción. Devuelve los papeles que quedan en vigor. */
    public List<FolderAssignment> replacePromotionFolders(String promotionId, Map<FolderRole, String> assignments) {
        if (repository.promotion(promotionId).isEmpty()) {
            throw new NotFoundException("No existe la promoción " + promotionId);
        }
        folders.replacePromotionFolders(promotionId, clean(assignments));
        events.publishEvent(new FactoryChanged("promotion", promotionId));
        return queries.folders(promotionId);
    }

    /** Rutas completas, sin espacios sobrantes. No se exige que existan: G: puede no estar montada ahora. */
    private static Map<FolderRole, String> clean(Map<FolderRole, String> assignments) {
        Map<FolderRole, String> clean = new EnumMap<>(FolderRole.class);
        assignments.forEach((role, location) -> {
            String value = location == null ? "" : location.strip();
            if (value.isEmpty()) return;
            try {
                if (!Path.of(value).isAbsolute()) {
                    throw new IllegalArgumentException("La carpeta de «" + role.label() + "» debe ser una ruta completa: " + value);
                }
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("Ruta no válida para «" + role.label() + "»: " + value);
            }
            clean.put(role, value);
        });
        return clean;
    }

    /** Guarda la identidad de la marca. Sus promociones la heredan. */
    public BrandIdentity saveIdentity(String brandId, BrandIdentity identity) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        identities.save(brandId, identity);
        events.publishEvent(new FactoryChanged("brand", brandId));
        return queries.identity(brandId);
    }

    public EditorialDecision addDecision(String brandId, String text) {
        if (repository.brand(brandId).isEmpty()) throw new NotFoundException("No existe la marca " + brandId);
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) throw new IllegalArgumentException("Escribe la decisión");
        EditorialDecision decision = decisions.add(brandId, clean, clock.instant());
        events.publishEvent(new FactoryChanged("brand", brandId));
        return decision;
    }

    public void deleteDecision(String brandId, long id) {
        if (!decisions.delete(brandId, id)) throw new NotFoundException("No existe esa decisión en la marca " + brandId);
        events.publishEvent(new FactoryChanged("brand", brandId));
    }
}
