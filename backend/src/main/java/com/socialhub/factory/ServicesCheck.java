package com.socialhub.factory;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** La comprobación periódica de los servicios de la fábrica (Claude, Publora, Instagram). */
@PrimaryPort
public interface ServicesCheck {

    /**
     * En modo real, deja en «Agentes y herramientas» tres servicios deducidos de lo que SocialHub ya sabe (sin llamadas
     * externas): «Claude en la nube», «Claude en el PC · Publer» y «Publora», además de la tarjeta de Instagram. Solo se
     * guarda cuando cambia algo.
     */
    void check();
}
