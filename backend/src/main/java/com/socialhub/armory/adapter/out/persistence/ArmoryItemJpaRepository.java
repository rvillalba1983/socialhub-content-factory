package com.socialhub.armory.adapter.out.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface ArmoryItemJpaRepository extends JpaRepository<ArmoryItemEntity, String> {
    List<ArmoryItemEntity> findAllByOrderByPosition();

    @Query("select coalesce(max(a.position), -1) + 1 from ArmoryItemEntity a")
    int nextPosition();
}
