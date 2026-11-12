package com.socialhub.publishing.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface InstagramAccountJpa extends JpaRepository<InstagramPersistence.AccountEntity, String> {}
