package com.socialhub.armory.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface ArmoryItemJpaRepository extends JpaRepository<ArmoryItemEntity, String> {
    List<ArmoryItemEntity> findAllByOrderByPosition();
}
