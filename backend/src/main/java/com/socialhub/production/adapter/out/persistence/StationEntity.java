package com.socialhub.production.adapter.out.persistence;

import com.socialhub.production.domain.StatusLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Tabla {@code station}. Envers guarda en {@code station_aud} los cambios de estado, pausa y nota. */
@Entity
@Table(name = "station")
@Audited
@Getter
@Setter
@NoArgsConstructor
class StationEntity {

    @Id
    private String id;

    @NotAudited private int position;
    @NotAudited private String name;
    @NotAudited private String description;

    @Enumerated(EnumType.STRING)
    private StatusLevel status;

    @NotAudited
    @Enumerated(EnumType.STRING)
    private @Nullable StatusLevel previousStatus;

    private boolean paused;
    private @Nullable String note;

    @NotAudited private String limitNote;

    @NotAudited
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private List<String> tools = new ArrayList<>();

    @Version
    private long version;
}
