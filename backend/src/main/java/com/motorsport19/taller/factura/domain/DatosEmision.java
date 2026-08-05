package com.motorsport19.taller.factura.domain;

import com.motorsport19.taller.cliente.domain.Cliente;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Todo lo que necesita saberse para emitir una factura.
 *
 * <p>Se agrupa en un objeto porque son muchos datos y casi todos son
 * <b>instantaneas</b>: los datos fiscales del emisor y del receptor, la matricula
 * y el codigo de la OT se copian dentro de la factura y no vuelven a leerse de
 * sus tablas de origen. Si el taller cambia de domicilio o el cliente corrige su
 * direccion, las facturas ya emitidas siguen diciendo lo que decian.
 */
public record DatosEmision(
        SerieFactura serie,
        int numero,
        long numeroRegistro,
        TipoFactura tipo,

        OrdenTrabajo ordenTrabajo,
        Factura facturaRectificada,
        TipoRectificativa tipoRectificativa,
        String motivoRectificacion,

        LocalDate fechaEmision,
        LocalDate fechaOperacion,
        Instant timestampEmision,

        DatosFiscales emisor,
        Cliente receptor,
        DatosFiscales datosReceptor,

        String matricula,
        String descripcionVehiculo,
        String codigoOt,

        String softwareNombre,
        String softwareVersion,
        String softwareNif,
        String urlVerificacionQr,

        Long creadoPor
) {
}
