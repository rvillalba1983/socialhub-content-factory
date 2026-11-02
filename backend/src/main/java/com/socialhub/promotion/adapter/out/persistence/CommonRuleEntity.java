package com.socialhub.promotion.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "common_rule")
@Getter
@NoArgsConstructor
class CommonRuleEntity {
    @Id private int position;
    private String text;
}
