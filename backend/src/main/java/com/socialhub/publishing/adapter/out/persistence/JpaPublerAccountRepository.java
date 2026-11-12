package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.Platform;
import com.socialhub.publishing.domain.PublerAccountRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaPublerAccountRepository implements PublerAccountRepository {

    private final PublerAccountJpaRepository jpa;

    @Override
    public Map<Platform, String> accounts(String brandId) {
        Map<Platform, String> map = new EnumMap<>(Platform.class);
        jpa.findByBrandIdOrderByPlatform(brandId).forEach(e -> map.put(e.getPlatform(), e.getAccountName()));
        return map;
    }

    @Override
    public Optional<String> account(String brandId, Platform platform) {
        return jpa.findById(new PublerAccountEntity.Key(brandId, platform)).map(PublerAccountEntity::getAccountName);
    }

    @Override
    public void replace(String brandId, Map<Platform, String> accounts) {
        jpa.deleteByBrand(brandId);
        jpa.flush();
        accounts.forEach((platform, name) -> jpa.save(new PublerAccountEntity(brandId, platform, name)));
    }

    @Override
    public List<String> brandsUsing(String accountName, Platform platform) {
        return jpa.using(accountName, platform).stream().map(PublerAccountEntity::getBrandId).toList();
    }
}
