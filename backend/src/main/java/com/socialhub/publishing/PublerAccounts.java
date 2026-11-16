package com.socialhub.publishing;

import com.socialhub.publishing.domain.Platform;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** Cuenta de Publer de cada marca y red, y las publicaciones retenidas por una cuenta llena. */
@PrimaryPort
public interface PublerAccounts {

    record Choice(Platform platform, String accountName) {}

    /** Publicaciones retenidas porque la marca no tiene cuenta de Publer en esa red. */
    record Missing(String brandId, String platform, String networkLabel, int held) {}

    /**
     * Cuenta de Publer con publicaciones retenidas por el límite del plan. {@code movable}: las que se pueden pasar ya a
     * Publora (su marca tiene Publora configurada para esa red). {@code withoutPublora}: «marca/red» de las que no.
     */
    record Full(String accountName, String platform, String networkLabel, int waiting, int movable,
                       List<String> withoutPublora) {}

    Map<Platform, String> accounts(String brandId);

    /** La cuenta de Publer de la marca en esa red, si Raúl la ha indicado. */
    Optional<String> accountFor(String brandId, Platform platform);

    /**
     * Marcas y redes con publicaciones retenidas por falta de cuenta de Publer (para «Qué hacer ahora»). Se calcula en
     * cada consulta: desaparece sola al indicar la cuenta.
     */
    List<Missing> missing();

    /** Cuentas de Publer llenas: con publicaciones retenidas por el límite (la marca sí tiene cuenta, así que no es por falta de ella). */
    List<Full> full();

    /**
     * Manda ya a Publora las publicaciones retenidas por el límite de esa cuenta y red (de todas las marcas que la
     * comparten) cuya marca tiene Publora configurada. Cada una sigue el flujo normal de Publora.
     *
     * @return cuántas han pasado
     */
    int moveToPublora(String accountName, Platform platform);

    /** Redes en las que la marca aún no tiene cuenta de Publer (con {@code held} a 0). */
    List<Missing> unset(String brandId);

    /** Sustituye las cuentas de la marca: una por red, con el nombre tal como sale en Publer. Un nombre vacío quita la red. */
    Map<Platform, String> save(String brandId, List<Choice> choices);
}
