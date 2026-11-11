package com.socialhub.publishing;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PubloraAccountRepository;
import com.socialhub.publishing.domain.PubloraGateway;
import com.socialhub.publishing.domain.SecretStore;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Clave de Publora (en un archivo local, nunca en la base) y cuenta de Publora de cada marca en cada red. */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class PubloraService {

    public record Status(boolean configured, @Nullable String keyHint, List<PubloraGateway.Connection> connections,
                         @Nullable String error) {}

    public record Choice(Platform platform, String connectionId) {}

    private final SecretStore secrets;
    private final PubloraGateway publora;
    private final PubloraAccountRepository accounts;
    private final PromotionQueries promotions;
    private final org.springframework.context.ApplicationEventPublisher events;

    public Status status() {
        String key = secrets.get(PubloraRoute.KEY).orElse(null);
        if (key == null) return new Status(false, null, List.of(), null);
        try {
            return new Status(true, hint(key), publora.connections(key), null);
        } catch (PubloraGateway.PubloraException e) {
            return new Status(true, hint(key), List.of(), e.getMessage());
        }
    }

    /** Comprueba la clave con Publora y, si vale, la guarda. */
    public Status saveKey(String key) {
        String clean = key.strip();
        if (clean.length() < 10 || clean.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("La clave no tiene buena forma: pégala entera, sin espacios");
        }
        List<PubloraGateway.Connection> connections;
        try {
            connections = publora.connections(clean);
        } catch (PubloraGateway.PubloraException e) {
            throw new IllegalArgumentException("No se ha guardado: " + e.getMessage());
        }
        secrets.put(PubloraRoute.KEY, clean);
        return new Status(true, hint(clean), connections, null);
    }

    /** Hay clave de Publora guardada (no se comprueba con Publora: sin llamadas externas). */
    public boolean hasKey() { return secrets.get(PubloraRoute.KEY).isPresent(); }

    /** Qué impide publicar con Publora. */
    public enum Gap { NONE, NO_KEY, DISMISSED, NO_ACCOUNT }

    /**
     * Qué falta para publicar en esa red con Publora (por orden): la clave, que la marca no la haya descartado con «No uso
     * Publora» o su cuenta de esa red. {@code NONE}: se puede.
     */
    @Transactional(readOnly = true)
    public Gap gap(String brandId, Platform platform) {
        // «No uso Publora» va antes que la clave: si la marca lo descartó, no hay nada que conectar
        if (accounts.dismissed(brandId)) return Gap.DISMISSED;
        if (!hasKey()) return Gap.NO_KEY;
        return accounts.account(brandId, platform).isPresent() ? Gap.NONE : Gap.NO_ACCOUNT;
    }

    public void deleteKey() { secrets.delete(PubloraRoute.KEY); }

    @Transactional(readOnly = true)
    public List<PubloraAccountRepository.Account> accounts(String brandId) {
        promotions.requireBrand(brandId);
        return accounts.accounts(brandId);
    }

    /** La marca tiene al menos una cuenta de Publora elegida. */
    @Transactional(readOnly = true)
    public boolean hasAccounts(String brandId) { return !accounts.accounts(brandId).isEmpty(); }

    /** Raúl ha marcado «No uso Publora» para la marca. */
    @Transactional(readOnly = true)
    public boolean isDismissed(String brandId) { return accounts.dismissed(brandId); }

    @Transactional
    public void setDismissed(String brandId, boolean dismissed) {
        promotions.requireBrand(brandId);
        accounts.setDismissed(brandId, dismissed);
        events.publishEvent(new com.socialhub.platform.live.FactoryChanged("publora", brandId));
    }

    /** Cada cuenta tiene que estar conectada en Publora y ser de su red. */
    @Transactional
    public List<PubloraAccountRepository.Account> saveAccounts(String brandId, List<Choice> choices) {
        promotions.requireBrand(brandId);
        EnumSet<Platform> seen = EnumSet.noneOf(Platform.class);
        for (Choice c : choices) {
            if (!seen.add(c.platform())) throw new IllegalArgumentException("Una sola cuenta por red");
        }
        List<PubloraAccountRepository.Account> list = new ArrayList<>();
        if (!choices.isEmpty()) {
            String key = secrets.get(PubloraRoute.KEY)
                    .orElseThrow(() -> new IllegalArgumentException("Primero pega la clave de la API de Publora"));
            List<PubloraGateway.Connection> connections;
            try {
                connections = publora.connections(key);
            } catch (PubloraGateway.PubloraException e) {
                throw new IllegalArgumentException("No se pueden comprobar las cuentas: " + e.getMessage());
            }
            for (Choice c : choices) {
                var connection = connections.stream().filter(x -> x.id().equals(c.connectionId())).findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Esa cuenta no está conectada en Publora: " + c.connectionId()));
                if (!connection.platform().equals(c.platform().name().toLowerCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("«" + connection.label() + "» no es de " + PubloraRoute.label(c.platform()));
                }
                list.add(new PubloraAccountRepository.Account(c.platform(), connection.id(), connection.label()));
            }
        }
        accounts.replace(brandId, list);
        events.publishEvent(new com.socialhub.platform.live.FactoryChanged("publora", brandId));
        return accounts.accounts(brandId);
    }

    private static String hint(String key) { return "…" + key.substring(Math.max(0, key.length() - 4)); }
}
