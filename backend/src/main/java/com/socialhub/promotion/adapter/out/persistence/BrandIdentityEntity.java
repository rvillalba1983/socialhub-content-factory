package com.socialhub.promotion.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tabla {@code brand_identity}. */
@Entity
@Table(name = "brand_identity")
@Getter
@Setter
@NoArgsConstructor
class BrandIdentityEntity {
    @Id private String brandId;
    private String tone;
    private String audience;
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") private List<String> forbiddenTopics = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") private List<String> preferredExpressions = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") private List<String> forbiddenExpressions = new ArrayList<>();
    @JdbcTypeCode(SqlTypes.ARRAY) @Column(columnDefinition = "text[]") private List<String> colors = new ArrayList<>();
    private String font;
    private String logo;
    private String visualStyle;
    private String photoStyle;
    private String callToAction;
    private String fixedHashtags;
}
