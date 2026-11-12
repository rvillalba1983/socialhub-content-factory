package com.socialhub.publishing.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

/** Cuenta de Instagram (API oficial) de cada marca: su identificador y el @usuario que devolvió Meta. */
@SecondaryPort
public interface InstagramAccountRepository {

    record Account(String igUserId, String username) {}

    Optional<Account> account(String brandId);

    void save(String brandId, Account account);

    void delete(String brandId);
}
