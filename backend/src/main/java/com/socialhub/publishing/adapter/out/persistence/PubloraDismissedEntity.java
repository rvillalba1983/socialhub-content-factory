package com.socialhub.publishing.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Tabla {@code brand_publora_dismissed}: una fila por marca que no usa Publora. */
@Entity
@Table(name = "brand_publora_dismissed")
@Getter
@NoArgsConstructor
@AllArgsConstructor
class PubloraDismissedEntity {

    @Id private String brandId;
}
