package com.motorsport19.taller.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Columnas de auditoria comunes a las entidades modificables.
 *
 * <p>Los campos {@code createdBy} y {@code updatedBy} los rellena Spring Data a
 * partir del {@code AuditorAware} configurado; hasta la fase 5 (autenticacion)
 * quedan vacios.
 *
 * <p>Las tablas de registro append-only (movimientos de stock, cambios de estado,
 * facturas y sus eventos) NO heredan de aqui: solo tienen fecha de creacion,
 * porque por definicion nunca se modifican.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class EntidadAuditable {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    /** Bloqueo optimista: protege frente a ediciones simultaneas desde mostrador y taller. */
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;
}
