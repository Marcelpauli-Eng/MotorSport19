package com.motorsport19.taller.factura.domain;

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

import java.math.BigDecimal;

/**
 * Desglose de base imponible y cuota por tipo de IVA de una factura.
 *
 * <p>Cada fila es la SUMA de las lineas con ese porcentaje, no un recalculo:
 * asi el desglose siempre cuadra al centimo con las lineas y con la cabecera,
 * cosa que verifica un trigger diferido al hacer commit.
 *
 * <p>Inmutable, como el resto de la factura.
 */
@Entity
@Table(name = "desglose_iva_factura")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DesgloseIvaFactura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_id", nullable = false, updatable = false)
    private Factura factura;

    @Column(name = "tipo_iva", nullable = false, updatable = false, length = 20)
    private String tipoIva;

    @Column(name = "porcentaje_iva", nullable = false, updatable = false, precision = 5, scale = 2)
    private BigDecimal porcentajeIva;

    @Column(name = "base_imponible", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal baseImponible;

    @Column(name = "cuota_iva", nullable = false, updatable = false, precision = 12, scale = 2)
    private BigDecimal cuotaIva;
}
