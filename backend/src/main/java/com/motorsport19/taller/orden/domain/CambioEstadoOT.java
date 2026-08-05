package com.motorsport19.taller.orden.domain;

import com.motorsport19.taller.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Anotacion del historial de estados de una OT: quien la movio, cuando y por que.
 *
 * <p>Registro append-only: los triggers de la base de datos rechazan UPDATE y
 * DELETE. El historial de una OT no se puede reescribir.
 */
@Entity
@Table(name = "cambio_estado_ot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CambioEstadoOT {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orden_trabajo_id", nullable = false, updatable = false)
    private OrdenTrabajo ordenTrabajo;

    /** Vacio solo en el registro de apertura de la OT. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", updatable = false, length = 20)
    private EstadoOT estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, updatable = false, length = 20)
    private EstadoOT estadoNuevo;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "motivo", updatable = false, length = 300)
    private String motivo;
}
