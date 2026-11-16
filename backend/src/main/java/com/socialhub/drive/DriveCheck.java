package com.socialhub.drive;

import org.jmolecules.architecture.hexagonal.PrimaryPort;

/** La comprobación periódica de las carpetas de Google Drive. */
@PrimaryPort
public interface DriveCheck {

    /**
     * En modo real, ¿están accesibles todas las carpetas con papel y las de «Recursos»? El resultado se guarda en la
     * armería (y sale en los avisos si falta alguna). Solo se guarda cuando cambia.
     */
    void check();
}
