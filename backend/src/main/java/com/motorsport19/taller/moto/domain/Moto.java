package com.motorsport19.taller.moto.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.common.domain.EntidadAuditable;
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
 * Moto de un cliente.
 *
 * <p>El historial de intervenciones no se modela como una coleccion: se consulta
 * a traves de {@code orden_trabajo.moto_id} para no cargar anos de ordenes cada
 * vez que se lee una ficha.
 */
@Entity
@Table(name = "moto")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Moto extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @Column(name = "matricula", nullable = false, length = 15)
    private String matricula;

    @Column(name = "marca", nullable = false, length = 60)
    private String marca;

    @Column(name = "modelo", nullable = false, length = 100)
    private String modelo;

    @Column(name = "anio")
    private Integer anio;

    /** Cilindrada en centimetros cubicos. */
    @Column(name = "cilindrada")
    private Integer cilindrada;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "numero_bastidor", length = 30)
    private String numeroBastidor;

    /** Ultimo kilometraje conocido. Se actualiza con el km de entrada de cada OT. */
    @Column(name = "km_actual", nullable = false)
    private Integer kmActual;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;
}
