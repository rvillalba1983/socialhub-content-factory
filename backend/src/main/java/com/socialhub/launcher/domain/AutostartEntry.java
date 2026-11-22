package com.socialhub.launcher.domain;

import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** La entrada que hace que SocialHub se abra al iniciar sesión (del usuario: no pide administrador ni toca el sistema). */
@SecondaryPort
public interface AutostartEntry {

    /** Este sistema y esta forma de ejecutar SocialHub permiten la entrada (Windows con el instalador). */
    boolean supported();

    boolean exists();

    /** Crea la entrada (o la deja apuntando a este programa) para abrirlo en segundo plano. Se puede repetir. */
    void register();

    /** La borra; si no existe, no hace nada. */
    void unregister();
}
