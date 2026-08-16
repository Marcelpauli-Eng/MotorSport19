package com.motorsport19.taller.factura.web.dto;

import com.motorsport19.taller.factura.domain.DesgloseIvaFactura;
import com.motorsport19.taller.factura.domain.Factura;
import com.motorsport19.taller.factura.domain.LineaFactura;
import com.motorsport19.taller.factura.domain.TipoFactura;
import com.motorsport19.taller.factura.domain.TipoRectificativa;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Factura completa.
 *
 * @param huellaVerificada resultado de recalcular la huella desde la cadena
 *                         canonica almacenada. Si viniera {@code false}, la fila
 *                         se habria alterado por fuera de la aplicacion
 */
public record FacturaResponse(
        Long id,
        String numeroCompleto,
        String serieCodigo,
        Integer ejercicio,
        Integer numero,
        TipoFactura tipo,
        String tipoDescripcion,
        /** Sin los datos fiscales del cliente: el antiguo tique. */
        boolean simplificada,

        Long ordenTrabajoId,
        String codigoOt,
        Long facturaRectificadaId,
        String facturaRectificadaNumero,
        TipoRectificativa tipoRectificativa,
        String motivoRectificacion,

        LocalDate fechaEmision,
        LocalDate fechaOperacion,
        Instant timestampEmision,

        DatosFiscalesResponse emisor,
        Long receptorId,
        DatosFiscalesResponse receptor,

        String matricula,
        String descripcionVehiculo,

        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal total,

        Long numeroRegistro,
        String huellaAnterior,
        String huella,
        String cadenaHuella,
        String algoritmoHuella,
        boolean huellaVerificada,
        String qrContenido,

        String softwareNombre,
        String softwareVersion,

        List<LineaFacturaResponse> lineas,
        List<DesgloseIvaResponse> desgloseIva
) {

    public static FacturaResponse de(Factura f) {
        return new FacturaResponse(
                f.getId(), f.getNumeroCompleto(), f.getSerieCodigo(), f.getEjercicio(), f.getNumero(),
                f.getTipo(), f.getTipo().getDescripcion(),
                f.isSimplificada(),

                f.getOrdenTrabajo() == null ? null : f.getOrdenTrabajo().getId(),
                f.getCodigoOt(),
                f.getFacturaRectificada() == null ? null : f.getFacturaRectificada().getId(),
                f.getFacturaRectificada() == null ? null : f.getFacturaRectificada().getNumeroCompleto(),
                f.getTipoRectificativa(), f.getMotivoRectificacion(),

                f.getFechaEmision(), f.getFechaOperacion(), f.getTimestampEmision(),

                DatosFiscalesResponse.de(f.getEmisor()),
                f.getReceptor() == null ? null : f.getReceptor().getId(),
                DatosFiscalesResponse.de(f.getDatosReceptor()),

                f.getMatricula(), f.getDescripcionVehiculo(),
                f.getBaseImponible(), f.getTotalIva(), f.getTotal(),

                f.getNumeroRegistro(), f.getHuellaAnterior(), f.getHuella(), f.getCadenaHuella(),
                f.getAlgoritmoHuella(), f.huellaEsCoherente(), f.getQrContenido(),

                f.getSoftwareNombre(), f.getSoftwareVersion(),

                f.getLineas().stream().map(LineaFacturaResponse::de).toList(),
                f.getDesgloseIva().stream().map(DesgloseIvaResponse::de).toList());
    }

    public record DatosFiscalesResponse(String nombre, String nif, String direccion, String codigoPostal,
                                        String ciudad, String provincia, String pais) {
        static DatosFiscalesResponse de(com.motorsport19.taller.factura.domain.DatosFiscales d) {
            return d == null ? null : new DatosFiscalesResponse(d.getNombre(), d.getNif(), d.getDireccion(),
                    d.getCodigoPostal(), d.getCiudad(), d.getProvincia(), d.getPais());
        }
    }

    public record LineaFacturaResponse(Integer numeroLinea, String tipo, String descripcion, String piezaSku,
                                       BigDecimal cantidad, BigDecimal precioUnitario,
                                       BigDecimal descuentoPct, String tipoIva, BigDecimal porcentajeIva,
                                       BigDecimal importeBruto, BigDecimal importeDescuento,
                                       BigDecimal baseImponible, BigDecimal cuotaIva, BigDecimal total) {
        static LineaFacturaResponse de(LineaFactura l) {
            var importes = l.importes();
            return new LineaFacturaResponse(l.getNumeroLinea(), l.getTipo().name(), l.getDescripcion(),
                    l.getPiezaSku(), l.getCantidad(), l.getPrecioUnitario(), l.getDescuentoPct(),
                    l.getTipoIva(), l.getPorcentajeIva(),
                    l.importeBruto(), l.importeDescuento(),
                    importes.baseImponible(), importes.cuotaIva(), importes.total());
        }
    }

    public record DesgloseIvaResponse(String tipoIva, BigDecimal porcentajeIva, BigDecimal baseImponible,
                                      BigDecimal cuotaIva) {
        static DesgloseIvaResponse de(DesgloseIvaFactura d) {
            return new DesgloseIvaResponse(d.getTipoIva(), d.getPorcentajeIva(), d.getBaseImponible(),
                    d.getCuotaIva());
        }
    }
}
