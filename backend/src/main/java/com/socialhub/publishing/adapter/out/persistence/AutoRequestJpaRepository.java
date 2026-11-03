package com.socialhub.publishing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface AutoRequestJpaRepository extends JpaRepository<AutoRequestEntity, AutoRequestEntity.Key> {}
