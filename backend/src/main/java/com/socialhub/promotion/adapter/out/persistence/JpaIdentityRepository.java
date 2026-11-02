package com.socialhub.promotion.adapter.out.persistence;

import com.socialhub.promotion.BrandIdentity;
import com.socialhub.promotion.domain.IdentityRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class JpaIdentityRepository implements IdentityRepository {

    private final BrandIdentityJpaRepository jpa;

    @Override
    public Optional<BrandIdentity> identity(String brandId) {
        return jpa.findById(brandId).map(e -> new BrandIdentity(e.getTone(), e.getAudience(), e.getForbiddenTopics(),
                e.getPreferredExpressions(), e.getForbiddenExpressions(), e.getColors(), e.getFont(), e.getLogo(),
                e.getVisualStyle(), e.getPhotoStyle(), e.getCallToAction(), e.getFixedHashtags()));
    }

    @Override
    public void save(String brandId, BrandIdentity identity) {
        var existing = jpa.findById(brandId);
        BrandIdentityEntity e = existing.orElseGet(BrandIdentityEntity::new);
        e.setBrandId(brandId);
        e.setTone(identity.tone());
        e.setAudience(identity.audience());
        e.setForbiddenTopics(new java.util.ArrayList<>(identity.forbiddenTopics()));
        e.setPreferredExpressions(new java.util.ArrayList<>(identity.preferredExpressions()));
        e.setForbiddenExpressions(new java.util.ArrayList<>(identity.forbiddenExpressions()));
        e.setColors(new java.util.ArrayList<>(identity.colors()));
        e.setFont(identity.font());
        e.setLogo(identity.logo());
        e.setVisualStyle(identity.visualStyle());
        e.setPhotoStyle(identity.photoStyle());
        e.setCallToAction(identity.callToAction());
        e.setFixedHashtags(identity.fixedHashtags());
        if (existing.isEmpty()) jpa.save(e);
    }
}
