package com.socialhub.publishing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface MetricsJpaRepository extends JpaRepository<MetricsEntity, String> {}
