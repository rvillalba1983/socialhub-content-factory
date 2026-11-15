package com.socialhub.promotion.domain;

import java.util.Optional;
import org.jmolecules.architecture.hexagonal.SecondaryPort;

@SecondaryPort
public interface IdentityRepository {

    Optional<BrandIdentity> identity(String brandId);

    void save(String brandId, BrandIdentity identity);
}
