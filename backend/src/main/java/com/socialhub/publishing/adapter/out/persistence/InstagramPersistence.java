package com.socialhub.publishing.adapter.out.persistence;

import com.socialhub.publishing.domain.InstagramAccountRepository;
import com.socialhub.publishing.domain.PublicMediaRepository;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Repository;

/** Cuenta de Instagram de cada marca (API oficial) e imágenes públicas de cada publicación (Fase 12). */
final class InstagramPersistence {

    private InstagramPersistence() {}

    /** Tabla {@code brand_instagram_account}. */
    @Entity(name = "InstagramAccountRow")
    @Table(name = "brand_instagram_account")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class AccountEntity {
        @Id private String brandId;
        private String igUserId;
        private String username;
    }

    /** Tabla {@code publication_public_media}. */
    @Entity(name = "PublicMediaRow")
    @Table(name = "publication_public_media")
    @IdClass(MediaEntity.Key.class)
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class MediaEntity {
        @Id private String publicationId;
        @Id private int position;
        private String sourcePath;
        private String fileName;
        private @Nullable String url;

        @EqualsAndHashCode @NoArgsConstructor @AllArgsConstructor
        static class Key implements Serializable {
            private String publicationId;
            private int position;
        }
    }

    @Repository
    @RequiredArgsConstructor
    static class JpaAccounts implements InstagramAccountRepository {
        private final InstagramAccountJpa jpa;

        @Override
        public Optional<Account> account(String brandId) {
            return jpa.findById(brandId).map(e -> new Account(e.getIgUserId(), e.getUsername()));
        }

        @Override
        public void save(String brandId, Account account) {
            jpa.saveAndFlush(new AccountEntity(brandId, account.igUserId(), account.username()));
        }

        @Override
        public void delete(String brandId) {
            if (jpa.existsById(brandId)) jpa.deleteById(brandId);
        }
    }

    @Repository
    @RequiredArgsConstructor
    static class JpaPublicMedia implements PublicMediaRepository {
        private final PublicMediaJpa jpa;

        @Override
        public void replace(String publicationId, List<Item> items) {
            jpa.deleteByPublication(publicationId);
            jpa.flush();
            int i = 0;
            for (Item item : items) {
                jpa.save(new MediaEntity(publicationId, i++, item.sourcePath(), item.fileName(), item.url()));
            }
            jpa.flush();
        }

        @Override
        public List<Item> items(String publicationId) {
            return jpa.findByPublicationIdOrderByPosition(publicationId).stream().map(JpaPublicMedia::toItem).toList();
        }

        @Override
        public Optional<Item> byFileName(String fileName) { return jpa.findByFileName(fileName).map(JpaPublicMedia::toItem); }

        @Override
        public void setUrl(String publicationId, String fileName, String url) {
            jpa.findByFileName(fileName).filter(e -> e.getPublicationId().equals(publicationId)).ifPresent(e -> {
                e.setUrl(url);
                jpa.saveAndFlush(e);
            });
        }

        @Override
        public void delete(String publicationId) {
            jpa.deleteByPublication(publicationId);
            jpa.flush();
        }

        private static Item toItem(MediaEntity e) {
            return new Item(e.getPublicationId(), e.getSourcePath(), e.getFileName(), e.getUrl());
        }
    }
}
