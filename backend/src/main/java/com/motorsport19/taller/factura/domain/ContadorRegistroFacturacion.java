package com.motorsport19.taller.factura.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Contador global del registro de facturacion, con una unica fila.
 *
 * <p>Da el orden total de la cadena de huellas: todas las facturas, sean de la
 * serie que sean, ocupan una posicion consecutiva en este registro.
 *
 * <p>Quien lo incrementa de verdad es el trigger de la base de datos al insertar
 * la factura. Desde Java solo se lee (con la fila bloqueada) para saber que
 * posicion toca.
 */
@Entity
@Table(name = "contador_registro_facturacion")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContadorRegistroFacturacion {

    public static final Integer ID_UNICO = 1;

    @Id
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "ultimo_numero", nullable = false, insertable = false, updatable = false)
    private Long ultimoNumero;

    /** Posicion que ocupara la siguiente factura. */
    public long siguientePosicion() {
        return ultimoNumero + 1;
    }
}
