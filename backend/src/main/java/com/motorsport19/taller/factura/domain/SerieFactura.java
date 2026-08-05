package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Serie de facturacion de un ejercicio.
 *
 * <p>{@link #ultimoNumero} es un contador <b>transaccional</b>: se incrementa con
 * la fila bloqueada dentro de la misma transaccion que inserta la factura. A
 * diferencia de una secuencia de PostgreSQL, si la transaccion hace rollback el
 * numero vuelve atras y la numeracion no queda con huecos.
 */
@Entity
@Table(name = "serie_factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SerieFactura extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Prefijo visible en el numero de factura (A, R, ...). */
    @Column(name = "codigo", nullable = false, length = 10)
    private String codigo;

    @Column(name = "ejercicio", nullable = false)
    private Integer ejercicio;

    @Column(name = "descripcion", nullable = false, length = 150)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoFactura tipo;

    @Column(name = "ultimo_numero", nullable = false)
    private Integer ultimoNumero;

    @Column(name = "activa", nullable = false)
    private boolean activa;
}
