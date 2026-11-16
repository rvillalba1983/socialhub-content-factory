/**
 * Registros para encontrar fallos: arranque (arranque.log, aunque Spring no llegue a iniciarse), excepciones que
 * nadie captura, fallos fatales de la JVM, errores 5xx de la API y filtro de secretos. Todo va a la carpeta de
 * registros de SocialHub ({@code %LOCALAPPDATA%\SocialHub\logs} en Windows).
 */
@org.springframework.modulith.NamedInterface("diagnostics")
@org.jspecify.annotations.NullMarked
package com.socialhub.platform.diagnostics;
