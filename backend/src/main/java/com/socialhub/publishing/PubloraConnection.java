package com.socialhub.publishing;

import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PubloraAccountRepository;
import com.socialhub.publishing.domain.PubloraGateway;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** La conexión con Publora: clave, estado y cuentas por marca. */
@PrimaryPort
public interface PubloraConnection {

    record Status(boolean configured, @Nullable String keyHint, List<PubloraGateway.Connection> connections,
                         @Nullable String error) {}

    record Choice(Platform platform, String connectionId) {}

    Status status();

    /** Comprueba la clave con Publora y, si vale, la guarda. */
    Status saveKey(String key);

    /** Hay clave de Publora guardada (no se comprueba con Publora: sin llamadas externas). */
    boolean hasKey();

    /** Qué impide publicar con Publora. */
    enum Gap { NONE, NO_KEY, DISMISSED, NO_ACCOUNT }

    /**
     * Qué falta para publicar en esa red con Publora (por orden): la clave, que la marca no la haya descartado con «No uso
     * Publora» o su cuenta de esa red. {@code NONE}: se puede.
     */
    Gap gap(String brandId, Platform platform);

    void deleteKey();

    List<PubloraAccountRepository.Account> accounts(String brandId);

    /** La marca tiene al menos una cuenta de Publora elegida. */
    boolean hasAccounts(String brandId);

    /** Raúl ha marcado «No uso Publora» para la marca. */
    boolean isDismissed(String brandId);

    void setDismissed(String brandId, boolean dismissed);

    /** Cada cuenta tiene que estar conectada en Publora y ser de su red. */
    List<PubloraAccountRepository.Account> saveAccounts(String brandId, List<Choice> choices);
}
