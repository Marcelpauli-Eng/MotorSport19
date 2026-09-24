package com.motorsport19.taller.agenda.domain;

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
 * Anotacion del historial de una cita: quien la movio, cuando y por que.
 *
 * <p>Registro append-only: los triggers de la base de datos rechazan UPDATE y
 * DELETE. Sin esto, la cita solo sabia decir en que estado esta ahora y quien
 * fue el ultimo en tocarla, que es justo lo que no sirve cuando hay que
 * reconstruir por que un hueco se perdio.
 */
@Entity
@Table(name = "cambio_estado_cita")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CambioEstadoCita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cita_id", nullable = false, updatable = false)
    private Cita cita;

    /** Vacio solo en el registro de alta. */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", updatable = false, length = 20)
    private EstadoCita estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, updatable = false, length = 20)
    private EstadoCita estadoNuevo;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "motivo", updatable = false, length = 300)
    private String motivo;

    static CambioEstadoCita alta(Cita cita, Usuario usuario) {
        return crear(cita, null, EstadoCita.PENDIENTE, usuario, "Cita dada de alta");
    }

    static CambioEstadoCita transicion(Cita cita, EstadoCita anterior, EstadoCita nuevo,
                                       Usuario usuario, String motivo) {
        return crear(cita, anterior, nuevo, usuario, motivo);
    }

    /**
     * Mover una cita de dia no cambia su estado, pero es un hecho de la agenda
     * tan digno de constar como los demas: el hueco de ayer quedo libre y el de
     * hoy ocupado. Se anota con el estado repetido a los dos lados.
     */
    static CambioEstadoCita reprogramacion(Cita cita, Usuario usuario, String motivo) {
        return crear(cita, cita.getEstado(), cita.getEstado(), usuario, motivo);
    }

    private static CambioEstadoCita crear(Cita cita, EstadoCita anterior, EstadoCita nuevo,
                                          Usuario usuario, String motivo) {
        CambioEstadoCita cambio = new CambioEstadoCita();
        cambio.cita = cita;
        cambio.estadoAnterior = anterior;
        cambio.estadoNuevo = nuevo;
        cambio.usuario = usuario;
        cambio.fecha = Instant.now();
        cambio.motivo = motivo == null || motivo.isBlank() ? null : motivo.trim();
        return cambio;
    }
}
