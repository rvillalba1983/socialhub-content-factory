package com.socialhub.platform;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionNumber;
import org.hibernate.envers.RevisionTimestamp;

/**
 * Revisión de Hibernate Envers (tabla {@code revinfo}). Igual que la de serie, salvo que pide cada número a la
 * secuencia sin guardar bloques en memoria: hay una base de datos por modo y una caché compartida mezclaría los
 * números de las dos.
 */
@Entity
@Table(name = "revinfo")
@RevisionEntity
@Getter
@Setter
public class AuditRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "revinfo_seq")
    @SequenceGenerator(name = "revinfo_seq", sequenceName = "revinfo_seq", allocationSize = 1)
    @RevisionNumber
    private int rev;

    @RevisionTimestamp
    @Column(name = "revtstmp")
    private long timestamp;
}
