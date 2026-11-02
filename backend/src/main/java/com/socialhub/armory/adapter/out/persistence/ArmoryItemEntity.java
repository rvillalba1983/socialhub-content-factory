package com.socialhub.armory.adapter.out.persistence;

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
@Table(name = "armory_item")
@Getter
@Setter
@NoArgsConstructor
class ArmoryItemEntity {
    @Id private String id;
    private String kind;
    private String name;
    private String description;
    private String status;
    private String note;
    private int position;
    @Column(name = "is_demo") private boolean demo;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> capabilities = new ArrayList<>();
}
