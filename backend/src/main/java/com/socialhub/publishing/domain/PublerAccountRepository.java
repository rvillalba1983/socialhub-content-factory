package com.socialhub.publishing.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Qué cuenta de Publer usa cada marca en cada red (el nombre tal como sale en Publer). Dos marcas pueden compartirla. */
@SecondaryPort
public interface PublerAccountRepository {

    /** Cuentas de la marca, por red. */
    Map<Platform, String> accounts(String brandId);

    Optional<String> account(String brandId, Platform platform);

    /** Sustituye las de la marca. */
    void replace(String brandId, Map<Platform, String> accounts);

    /** Marcas que usan esa cuenta en esa red (se compara sin mayúsculas ni espacios de más). */
    List<String> brandsUsing(String accountName, Platform platform);
}
