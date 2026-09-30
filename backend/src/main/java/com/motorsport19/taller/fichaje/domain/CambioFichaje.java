package com.motorsport19.taller.fichaje.domain;

import com.motorsport19.taller.usuario.domain.Usuario;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Un cambio a mano en las horas de una jornada: que habia, que se puso, quien
 * y por que.
 *
 * <p>Registro append-only: la base de datos rechaza UPDATE y DELETE. Si una
 * correccion estaba mal, se corrige con otra y quedan las dos.
 */
@Entity
@Table(name = "cambio_fichaje")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CambioFichaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fichaje_id", nullable = false, updatable = false)
    private Fichaje fichaje;

    @Column(name = "inicio_anterior", nullable = false, updatable = false)
    private Instant inicioAnterior;

    /** Nula si la jornada estaba abierta: se fue sin fichar la salida. */
    @Column(name = "fin_anterior", updatable = false)
    private Instant finAnterior;

    @Column(name = "inicio_nuevo", nullable = false, updatable = false)
    private Instant inicioNuevo;

    @Column(name = "fin_nuevo", nullable = false, updatable = false)
    private Instant finNuevo;

    @Column(name = "motivo", nullable = false, updatable = false, length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", updatable = false)
    private Usuario usuario;

    @Column(name = "fecha", nullable = false, updatable = false)
    private Instant fecha;

    static CambioFichaje de(Fichaje fichaje, Instant inicioAnterior, Instant finAnterior,
                            String motivo, Usuario usuario) {
        CambioFichaje cambio = new CambioFichaje();
        cambio.fichaje = fichaje;
        cambio.inicioAnterior = inicioAnterior;
        cambio.finAnterior = finAnterior;
        cambio.inicioNuevo = fichaje.inicioReal();
        cambio.finNuevo = fichaje.finReal();
        cambio.motivo = motivo;
        cambio.usuario = usuario;
        cambio.fecha = Instant.now();
        return cambio;
    }
}
