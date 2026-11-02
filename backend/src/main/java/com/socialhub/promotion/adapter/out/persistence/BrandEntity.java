package com.socialhub.promotion.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
