package com.socialhub.ai;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Ajuste del orquestador (tabla {@code ai_setting}). */
@Entity
@Table(name = "ai_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class AiSettingEntity {
    @Id private String key;
    private String value;
}
