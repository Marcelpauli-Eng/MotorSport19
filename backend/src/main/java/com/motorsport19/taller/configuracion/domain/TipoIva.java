package com.motorsport19.taller.configuracion.domain;

import com.motorsport19.taller.common.domain.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Tipo de IVA aplicable (GENERAL, REDUCIDO, SUPERREDUCIDO, EXENTO).
 *
 * <p>Es un catalogo configurable, no un enum: los porcentajes los fija la ley y
 * pueden cambiar. Por eso cada linea de OT y de factura guarda ademas una COPIA
 * del porcentaje aplicado: un cambio normativo no altera documentos ya emitidos.
 */
@Entity
@Table(name = "tipo_iva")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TipoIva extends EntidadAuditable {

    /** Codigo tecnico que usan las lineas: GENERAL, REDUCIDO, SUPERREDUCIDO, EXENTO. */
    @Id
    @Column(name = "codigo", nullable = false, length = 20)
    private String codigo;

    @Column(name = "descripcion", nullable = false, length = 100)
    private String descripcion;

    @Column(name = "porcentaje", nullable = false, precision = 5, scale = 2)
    private BigDecimal porcentaje;

    @Column(name = "activo", nullable = false)
    private boolean activo;
}
