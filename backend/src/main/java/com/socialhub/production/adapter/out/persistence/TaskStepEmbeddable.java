package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.StepState;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class TaskStepEmbeddable {
    private String label;

    @Enumerated(EnumType.STRING)
    private StepState state;
}
