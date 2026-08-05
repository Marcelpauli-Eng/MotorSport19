package com.motorsport19.taller.cliente.domain;

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

import java.time.Instant;

/**
 * Cliente del taller.
 *
 * <p>La ficha puede crearse incompleta (alguien que entra con una averia y deja
 * solo nombre y telefono), pero los datos fiscales son obligatorios ANTES de
 * poder emitir una factura. Esa comprobacion se implementa en la fase 4.
 *
 * <p>Baja logica mediante {@code activo}: nunca se borra fisicamente porque
 * queda referenciado desde motos, ordenes de trabajo y facturas.
 */
@Entity
@Table(name = "cliente")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cliente extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    /** Vacio en personas juridicas. */
    @Column(name = "apellidos", length = 150)
    private String apellidos;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento", length = 10)
    private TipoDocumento tipoDocumento;

    /** NIF / CIF / NIE. Unico cuando esta informado (indice unico sobre mayusculas). */
    @Column(name = "documento", length = 20)
    private String documento;

    @Column(name = "direccion", length = 200)
    private String direccion;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Column(name = "provincia", length = 100)
    private String provincia;

    @Column(name = "pais", nullable = false, length = 60)
    private String pais;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "observaciones", columnDefinition = "text")
    private String observaciones;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "fecha_baja")
    private Instant fechaBaja;
}
