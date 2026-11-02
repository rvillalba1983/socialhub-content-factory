package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.AgentKind;
import jakarta.persistence.Column;
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
class AgentEmbeddable {
    @Enumerated(EnumType.STRING)
    @Column(name = "agent_kind")
    private AgentKind kind;

    @Column(name = "agent_name")
    private String name;
}
