package com.motorsport19.taller.factura.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Datos fiscales copiados dentro de una factura.
 *
 * <p>Es un objeto de valor: una vez escrito en la factura no cambia nunca, ni
 * aunque cambien los datos del taller o del cliente. Por eso se guarda como
 * copia y no como referencia.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DatosFiscales {

    /** Razon social o nombre y apellidos. */
    @Column(name = "nombre", nullable = false, length = 200)
    private String nombre;

    @Column(name = "nif", nullable = false, length = 20)
    private String nif;

    @Column(name = "direccion", nullable = false, length = 200)
    private String direccion;

    @Column(name = "codigo_postal", nullable = false, length = 10)
    private String codigoPostal;

    @Column(name = "ciudad", nullable = false, length = 100)
    private String ciudad;

    @Column(name = "provincia", nullable = false, length = 100)
    private String provincia;

    @Column(name = "pais", nullable = false, length = 60)
    private String pais;

    public DatosFiscales(String nombre, String nif, String direccion, String codigoPostal,
                         String ciudad, String provincia, String pais) {
        this.nombre = nombre;
        this.nif = nif;
        this.direccion = direccion;
        this.codigoPostal = codigoPostal;
        this.ciudad = ciudad;
        this.provincia = provincia;
        this.pais = pais;
    }
}
