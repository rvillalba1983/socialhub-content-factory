package com.socialhub.publishing;

import com.socialhub.armory.ArmoryService;
import com.socialhub.platform.AppMode;
import com.socialhub.platform.AppModeHolder;
import com.socialhub.platform.live.FactoryChanged;
import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.InstagramAccountRepository;
import com.socialhub.publishing.domain.InstagramGateway;
import com.socialhub.publishing.domain.InstagramGateway.InstagramException;
import com.socialhub.publishing.domain.SecretStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API oficial de Instagram: la clave de usuario del sistema de Meta (en un archivo local, nunca en la base) y la cuenta de
 * Instagram de cada marca. La clave no caduca y vale para todas las marcas del porfolio; no permite listar cuentas, así
 * que cada marca guarda el identificador de la suya y SocialHub lo comprueba con Meta y enseña el @usuario.
 */
@Slf4j
@PrimaryPort
@Service
@RequiredArgsConstructor
public class InstagramService {

    public static final String ARMORY_ID = "instagram-api";
    public static final String ARMORY_NAME = "Instagram · API oficial";
    public static final String ARMORY_DESCRIPTION = "Publica en Instagram por la API oficial de Meta (clave de usuario del sistema). "
            + "SocialHub publica a la hora; si falla, la publicación sigue por Publer. La clave vive en la carpeta de datos de SocialHub.";

    public record Status(boolean configured, @Nullable String keyHint) {}

    public record BrandCheck(String brandId, String brandName, @Nullable String username, @Nullable Integer used,
                             @Nullable Integer total, @Nullable String error) {}

    public record Check(boolean ok, @Nullable String name, @Nullable String error, List<BrandCheck> brands) {}

    /** Estado para «Agentes y herramientas»: {@code OK}, {@code WAITING}, {@code IDLE} o {@code FAILED}. */
    public record Health(String status, String note) {}

    private final SecretStore secrets;
    private final InstagramGateway instagram;
    private final InstagramAccountRepository accounts;
    private final PromotionQueries promotions;
    private final ArmoryService armory;
    private final ApplicationEventPublisher events;
    private volatile @Nullable Check last;

    public Status status() {
        String key = secrets.get(InstagramRoute.KEY).orElse(null);
        return new Status(key != null, key == null ? null : hint(key));
    }

    public boolean hasKey() { return secrets.get(InstagramRoute.KEY).isPresent(); }

    /** Comprueba la clave con Meta (GET /me) y, si vale, la guarda. */
    public Status saveKey(String key) {
        String clean = key.strip();
        if (clean.length() < 10 || clean.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("La clave no tiene buena forma: pégala entera, sin espacios");
        }
        try {
            instagram.me(clean);
        } catch (InstagramException e) {
            throw new IllegalArgumentException("No se ha guardado: " + e.getMessage());
        }
        secrets.put(InstagramRoute.KEY, clean);
        last = null;
        refreshArmory();
        return new Status(true, hint(clean));
    }

    public void deleteKey() {
        secrets.delete(InstagramRoute.KEY);
        last = null;
        refreshArmory();
    }

    /** GET /me y, de cada marca con cuenta, GET /{ig-id} y el límite de publicación. Los fallos de Meta van en el resultado. */
    public Check check() {
        String key = secrets.get(InstagramRoute.KEY).orElse(null);
        if (key == null) throw new IllegalArgumentException("Primero pega la clave de usuario del sistema de Meta");
        String name = null;
        String error = null;
        try {
            name = instagram.me(key).name();
        } catch (InstagramException e) {
            error = e.getMessage();
        }
        List<BrandCheck> brands = new ArrayList<>();
        for (var brand : promotions.brandRefs()) {
            var account = accounts.account(brand.id()).orElse(null);
            if (account == null) continue;
            if (error != null) {
                brands.add(new BrandCheck(brand.id(), brand.name(), account.username(), null, null, "no se ha podido comprobar"));
                continue;
            }
            try {
                String username = instagram.account(key, account.igUserId()).username();
                var quota = instagram.publishingLimit(key, account.igUserId());
                brands.add(new BrandCheck(brand.id(), brand.name(), username, quota.used(), quota.total(), null));
            } catch (InstagramException e) {
                brands.add(new BrandCheck(brand.id(), brand.name(), account.username(), null, null, e.getMessage()));
            }
        }
        boolean ok = error == null && brands.stream().noneMatch(b -> b.error() != null);
        Check check = new Check(ok, name, error, brands);
        last = check;
        refreshArmory();
        return check;
    }

    /** Qué enseña «Agentes y herramientas» (sin llamadas a Meta). */
    public Health health() {
        if (!hasKey()) return new Health("IDLE", "Sin clave: pégala en esta tarjeta");
        Check c = last;
        if (c == null) return new Health("WAITING", "Clave guardada, sin comprobar todavía");
        if (c.error() != null) return new Health("FAILED", "Meta no acepta la clave: " + c.error());
        String brands = c.brands().isEmpty() ? "ninguna marca tiene aún su cuenta"
                : String.join(" · ", c.brands().stream().map(b -> (b.username() == null ? b.brandName() : "@" + b.username())
                        + (b.error() != null ? " (" + b.error() + ")" : b.used() == null ? "" : " " + b.used() + "/" + b.total())).toList());
        return new Health(c.ok() ? "OK" : "FAILED", "Clave válida · " + brands);
    }

    @Transactional(readOnly = true)
    public Optional<InstagramAccountRepository.Account> account(String brandId) {
        promotions.requireBrand(brandId);
        return accounts.account(brandId);
    }

    /** Valida el identificador con Meta (GET /{ig-id}?fields=id,username) y guarda el @usuario. Vacío: quita la cuenta. */
    @Transactional
    public Optional<InstagramAccountRepository.Account> saveAccount(String brandId, String igUserId) {
        promotions.requireBrand(brandId);
        String id = igUserId.strip();
        if (id.isEmpty()) {
            accounts.delete(brandId);
            changed(brandId);
            return Optional.empty();
        }
        if (!id.matches("\\d{5,30}")) {
            throw new IllegalArgumentException("El identificador de la cuenta de Instagram es un número largo (solo cifras)");
        }
        String key = secrets.get(InstagramRoute.KEY)
                .orElseThrow(() -> new IllegalArgumentException("Primero pega la clave de Meta en «Agentes y herramientas» › Instagram · API oficial"));
        InstagramGateway.Account found;
        try {
            found = instagram.account(key, id);
        } catch (InstagramException e) {
            throw new IllegalArgumentException("No se ha guardado: " + e.getMessage());
        }
        var account = new InstagramAccountRepository.Account(id, found.username());
        accounts.save(brandId, account);
        last = null;
        changed(brandId);
        return Optional.of(account);
    }

    private void changed(String brandId) {
        events.publishEvent(new FactoryChanged("instagram", brandId));
        refreshArmory();
    }

    private void refreshArmory() {
        Health h = health();
        try {
            AppModeHolder.callIn(AppMode.REAL, () -> {
                armory.registerService(ARMORY_ID, ARMORY_NAME, ARMORY_DESCRIPTION, h.status(), h.note(), List.of("Publicar", "Instagram"));
                return null;
            });
        } catch (RuntimeException e) {
            log.warn("No se pudo actualizar el estado de la API oficial de Instagram en la armería: {}", e.toString());
        }
    }

    private static String hint(String key) { return "…" + key.substring(Math.max(0, key.length() - 4)); }
}
