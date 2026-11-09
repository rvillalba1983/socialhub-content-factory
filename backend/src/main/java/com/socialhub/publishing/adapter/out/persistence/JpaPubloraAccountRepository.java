package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PubloraAccountRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPubloraAccountRepository implements PubloraAccountRepository {

    private final PubloraAccountJpaRepository jpa;
    private final PubloraDismissedJpaRepository dismissedJpa;

    @Override
    public List<Account> accounts(String brandId) {
        return jpa.findByBrandIdOrderByPlatform(brandId).stream()
                .map(e -> new Account(e.getPlatform(), e.getConnectionId(), e.getLabel())).toList();
    }

    @Override
    public Optional<Account> account(String brandId, Platform platform) {
        return jpa.findById(new PubloraAccountEntity.Key(brandId, platform))
                .map(e -> new Account(e.getPlatform(), e.getConnectionId(), e.getLabel()));
    }

    @Override
    public void replace(String brandId, List<Account> accounts) {
        jpa.deleteByBrand(brandId);
        jpa.flush();
        accounts.forEach(a -> jpa.save(new PubloraAccountEntity(brandId, a.platform(), a.connectionId(), a.label())));
    }

    @Override
    public boolean dismissed(String brandId) { return dismissedJpa.existsById(brandId); }

    @Override
    public void setDismissed(String brandId, boolean dismissed) {
        if (dismissed) dismissedJpa.save(new PubloraDismissedEntity(brandId));
        else dismissedJpa.deleteById(brandId);
    }
}
