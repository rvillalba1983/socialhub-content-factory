package com.socialhub.publishing;

import com.socialhub.publishing.domain.InstagramAccountRepository;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.jspecify.annotations.Nullable;

/** La conexión con la API oficial de Instagram: clave, comprobación y cuentas por marca. */
@PrimaryPort
public interface InstagramConnection {

    String ARMORY_ID = "instagram-api";
    String ARMORY_NAME = "Instagram · API oficial";
    String ARMORY_DESCRIPTION = "Publica en Instagram por la API oficial de Meta (clave de usuario del sistema). "
            + "SocialHub publica a la hora; si falla, la publicación sigue por Publer. La clave vive en la carpeta de datos de SocialHub.";

    record Status(boolean configured, @Nullable String keyHint) {}

    record BrandCheck(String brandId, String brandName, @Nullable String username, @Nullable Integer used,
                             @Nullable Integer total, @Nullable String error) {}

    record Check(boolean ok, @Nullable String name, @Nullable String error, List<BrandCheck> brands) {}

    /** Estado para «Agentes y herramientas»: {@code OK}, {@code WAITING}, {@code IDLE} o {@code FAILED}. */
    record Health(String status, String note) {}

    Status status();

    /** Ya hay un resultado de {@link #check()} vigente (se pierde al guardar la clave o una cuenta y al reiniciar). */
    boolean hasCheck();

    /** Hay con qué llamar a Meta: una conexión vigente o la clave de usuario del sistema. */
    boolean hasKey();

    /** Comprueba la clave con Meta (GET /me) y, si vale, la guarda. */
    Status saveKey(String key);

    void deleteKey();

    /** GET /me y, de cada marca con cuenta, GET /{ig-id} y el límite de publicación. Los fallos de Meta van en el resultado. */
    Check check();

    /** Qué enseña «Agentes y herramientas» (sin llamadas a Meta). */
    Health health();

    /** Guarda la cuenta de una marca sin llamar a Meta: el @usuario ya lo devolvió Meta al listar las páginas. */
    void assignDetected(String brandId, String igUserId, String username);

    /** Vuelve a calcular el estado de la tarjeta (tras conectar o desconectar). */
    void refresh();

    Optional<InstagramAccountRepository.Account> account(String brandId);

    /** Valida el identificador con Meta (GET /{ig-id}?fields=id,username) y guarda el @usuario. Vacío: quita la cuenta. */
    Optional<InstagramAccountRepository.Account> saveAccount(String brandId, String igUserId);
}
