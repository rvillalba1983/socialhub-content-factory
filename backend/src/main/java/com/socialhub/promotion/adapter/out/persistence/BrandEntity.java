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

@Entity
@Table(name = "brand")
@Getter
@Setter
@NoArgsConstructor
class BrandEntity {
    @Id private String id;
    private String name;
    private String handle;
    private String color;
    private int position;
    @Column(name = "is_demo") private boolean demo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "kit_pieces", columnDefinition = "text[]")
    private List<String> kitPieces = new ArrayList<>();
}
