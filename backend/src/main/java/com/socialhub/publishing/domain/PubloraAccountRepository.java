package com.socialhub.publishing.domain;

import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Qué cuenta de Publora usa cada marca en cada red. */
@SecondaryPort
public interface PubloraAccountRepository {

    record Account(Platform platform, String connectionId, String label) {}

    List<Account> accounts(String brandId);

    Optional<Account> account(String brandId, Platform platform);

    /** Sustituye las de la marca. */
    void replace(String brandId, List<Account> accounts);

    /** Raúl ha marcado «No uso Publora» para la marca. */
    boolean dismissed(String brandId);

    void setDismissed(String brandId, boolean dismissed);
}
