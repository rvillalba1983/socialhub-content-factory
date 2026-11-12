package com.socialhub.publishing;

import com.socialhub.promotion.PromotionQueries;
import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublerAccountRepository;
import com.socialhub.publishing.domain.PublicationRepository;
import com.socialhub.publishing.domain.PublishJob;
import com.socialhub.publishing.domain.PublishJobRepository;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cuenta de Publer de cada marca en cada red. Publer no tiene API en el plan gratuito: Claude programa con Chrome y
 * necesita saber en qué cuenta. Dos marcas pueden compartir cuenta (la de TikTok).
 */
@PrimaryPort
@Service
@RequiredArgsConstructor
public class PublerAccountService {

    public record Choice(Platform platform, String accountName) {}

    static final int MAX_NAME = 120;

    /** Publicaciones retenidas porque la marca no tiene cuenta de Publer en esa red. */
    public record Missing(String brandId, String platform, String networkLabel, int held) {}

    /**
     * Cuenta de Publer con publicaciones retenidas por el límite del plan. {@code movable}: las que se pueden pasar ya a
     * Publora (su marca tiene Publora configurada para esa red). {@code withoutPublora}: «marca/red» de las que no.
     */
    public record Full(String accountName, String platform, String networkLabel, int waiting, int movable,
                       List<String> withoutPublora) {}

    private final PublerAccountRepository accounts;
    private final PubloraService publora;
    private final PublishingEngine engine;
    private final PublishJobRepository jobs;
    private final PublicationRepository publications;
    private final PromotionQueries promotions;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Map<Platform, String> accounts(String brandId) {
        promotions.requireBrand(brandId);
        return accounts.accounts(brandId);
    }

    /** La cuenta de Publer de la marca en esa red, si Raúl la ha indicado. */
    @Transactional(readOnly = true)
    public Optional<String> accountFor(String brandId, Platform platform) {
        return accounts.account(brandId, platform);
    }

    /**
     * Marcas y redes con publicaciones retenidas por falta de cuenta de Publer (para «Qué hacer ahora»). Se calcula en
     * cada consulta: desaparece sola al indicar la cuenta.
     */
    @Transactional(readOnly = true)
    public List<Missing> missing() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PublishJob job : jobs.withStatus(java.util.EnumSet.of(PublishJob.Status.HELD))) {
            var p = publications.findById(job.getPublicationId()).orElse(null);
            if (p == null) continue;
            String brandId = promotions.brandOf(p.getPromotionId());
            if (accounts.account(brandId, p.getPlatform()).isPresent()) continue;
            counts.merge(brandId + "|" + p.getPlatform().name(), 1, Integer::sum);
        }
        return counts.entrySet().stream().map(e -> {
            String[] key = e.getKey().split("\\|");
            Platform platform = Platform.valueOf(key[1]);
            return new Missing(key[0], platform.name(), PubloraRoute.label(platform), e.getValue());
        }).toList();
    }

    private static String key(String accountName, Platform platform) {
        return accountName.strip().toLowerCase(java.util.Locale.ROOT) + "|" + platform.name();
    }

    /** Cuentas de Publer llenas: con publicaciones retenidas por el límite (la marca sí tiene cuenta, así que no es por falta de ella). */
    @Transactional(readOnly = true)
    public List<Full> full() {
        var names = promotions.brandRefs().stream().collect(java.util.stream.Collectors.toMap(
                PromotionQueries.BrandRef::id, PromotionQueries.BrandRef::name, (a, b) -> a));
        Map<String, String> display = new LinkedHashMap<>();
        Map<String, int[]> counts = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> without = new LinkedHashMap<>();
        for (PublishJob job : jobs.withStatus(java.util.EnumSet.of(PublishJob.Status.HELD))) {
            var p = publications.findById(job.getPublicationId()).orElse(null);
            if (p == null) continue;
            String brandId = promotions.brandOf(p.getPromotionId());
            var account = accounts.account(brandId, p.getPlatform());
            if (account.isEmpty()) continue;
            String k = key(account.get(), p.getPlatform());
            display.putIfAbsent(k, account.get());
            int[] c = counts.computeIfAbsent(k, x -> new int[2]);
            c[0]++;
            if (publora.gap(brandId, p.getPlatform()) == PubloraService.Gap.NONE) c[1]++;
            else without.computeIfAbsent(k, x -> new java.util.LinkedHashSet<>())
                    .add(names.getOrDefault(brandId, brandId) + "/" + PubloraRoute.label(p.getPlatform()));
        }
        List<Full> list = new java.util.ArrayList<>();
        counts.forEach((k, c) -> {
            Platform platform = Platform.valueOf(k.substring(k.indexOf('|') + 1));
            list.add(new Full(display.get(k), platform.name(), PubloraRoute.label(platform), c[0], c[1],
                    List.copyOf(without.getOrDefault(k, java.util.Set.of()))));
        });
        return list;
    }

    /**
     * Manda ya a Publora las publicaciones retenidas por el límite de esa cuenta y red (de todas las marcas que la
     * comparten) cuya marca tiene Publora configurada. Cada una sigue el flujo normal de Publora.
     *
     * @return cuántas han pasado
     */
    @Transactional
    public int moveToPublora(String accountName, Platform platform) {
        String wanted = key(accountName, platform);
        int moved = 0;
        for (PublishJob job : jobs.withStatus(java.util.EnumSet.of(PublishJob.Status.HELD))) {
            var p = publications.findById(job.getPublicationId()).orElse(null);
            if (p == null || p.getPlatform() != platform) continue;
            String brandId = promotions.brandOf(p.getPromotionId());
            var account = accounts.account(brandId, platform);
            if (account.isEmpty() || !key(account.get(), platform).equals(wanted)) continue;
            if (publora.gap(brandId, platform) != PubloraService.Gap.NONE) continue;
            if (engine.moveHeld(p.getId(), PublishRoute.Id.PUBLORA)) moved++;
        }
        if (moved == 0) throw new IllegalArgumentException("Ninguna publicación en espera de esa cuenta tiene Publora configurada");
        events.publishEvent(new com.socialhub.platform.live.FactoryChanged("publer", accountName));
        return moved;
    }

    /** Redes en las que la marca aún no tiene cuenta de Publer (con {@code held} a 0). */
    @Transactional(readOnly = true)
    public List<Missing> unset(String brandId) {
        var have = accounts.accounts(brandId);
        return java.util.Arrays.stream(Platform.values()).filter(p -> !have.containsKey(p))
                .map(p -> new Missing(brandId, p.name(), PubloraRoute.label(p), 0)).toList();
    }

    /** Sustituye las cuentas de la marca: una por red, con el nombre tal como sale en Publer. Un nombre vacío quita la red. */
    @Transactional
    public Map<Platform, String> save(String brandId, List<Choice> choices) {
        promotions.requireBrand(brandId);
        Map<Platform, String> map = new EnumMap<>(Platform.class);
        java.util.Set<Platform> seen = java.util.EnumSet.noneOf(Platform.class);
        for (Choice c : choices) {
            if (!seen.add(c.platform())) throw new IllegalArgumentException("Una sola cuenta por red");
            String name = c.accountName() == null ? "" : c.accountName().strip().replaceAll("\\s+", " ");
            if (name.isEmpty()) continue;
            if (name.length() > MAX_NAME) {
                throw new IllegalArgumentException("El nombre de la cuenta es demasiado largo (máximo " + MAX_NAME + ")");
            }
            map.put(c.platform(), name);
        }
        accounts.replace(brandId, map);
        events.publishEvent(new com.socialhub.platform.live.FactoryChanged("publer", brandId));
        return accounts.accounts(brandId);
    }
}
