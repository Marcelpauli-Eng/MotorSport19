package com.motorsport19.taller.orden.web.dto;

import com.motorsport19.taller.orden.domain.CambioEstadoOT;
import com.motorsport19.taller.orden.domain.EstadoOT;
import com.motorsport19.taller.orden.domain.LineaOT;
import com.motorsport19.taller.orden.domain.OrdenTrabajo;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Ficha completa de una orden de trabajo.
 *
 * @param estadosPosibles a que estados puede saltar desde donde esta. El frontend
 *                        lo usa para pintar solo los botones que tienen sentido,
 *                        en vez de dejar al usuario probar y recibir un error.
 * @param facturable      si esta en un estado desde el que se puede emitir factura
 */
public record OrdenTrabajoResponse(
        Long id,
        String codigo,
        Integer ejercicio,
        Integer numero,
        EstadoOT estado,
        String estadoDescripcion,
        Set<EstadoOT> estadosPosibles,
        boolean facturable,
        boolean permiteEditarLineas,

        Long motoId,
        String matricula,
        String descripcionMoto,
        Long clienteId,
        String clienteNombre,
        /** Para mandarle el presupuesto por WhatsApp sin abrir su ficha. */
        String clienteTelefono,
        /**
         * Si el cliente reune los datos fiscales que exige una factura.
         *
         * <p>Viaja con la orden para poder avisar ANTES de pulsar «Emitir
         * factura». Sin esto el mostrador se entera de que falta el DNI o el
         * domicilio en el momento de facturar, que es el peor momento: el
         * cliente ya esta delante esperando el papel.
         */
        boolean clienteFacturable,

        Instant fechaEntrada,
        LocalDate fechaEstimadaSalida,
        Instant fechaRealSalida,
        Integer kmEntrada,

        String problemaReportado,
        String diagnostico,
        Long tecnicoId,
        String tecnicoNombre,

        BigDecimal tarifaHora,
        /** Tipo de IVA impuesto a toda la orden, o nulo si cada linea lleva el suyo. */
        String tipoIva,
        Instant fechaPresupuesto,
        Instant fechaAprobacion,
        String aprobadoPor,
        String motivoRechazo,
        String observaciones,

        BigDecimal importeBruto,
        BigDecimal totalDescuento,
        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal total,
        BigDecimal horasManoDeObra,

        List<LineaOTResponse> lineas,
        List<CambioEstadoResponse> historial
) {

    public static OrdenTrabajoResponse de(OrdenTrabajo orden, List<LineaOT> lineas,
                                          List<CambioEstadoOT> historial) {
        BigDecimal bruto = sumar(lineas, LineaOT::importeBruto);
        BigDecimal descuento = sumar(lineas, LineaOT::importeDescuento);
        BigDecimal base = sumar(lineas, LineaOT::getBaseImponible);
        BigDecimal iva = sumar(lineas, LineaOT::getCuotaIva);
        BigDecimal total = sumar(lineas, LineaOT::getTotal);
        BigDecimal horas = lineas.stream()
                .filter(l -> !l.esDePieza())
                .map(LineaOT::getCantidad)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new OrdenTrabajoResponse(
                orden.getId(),
                orden.codigoVisible(),
                orden.getEjercicio(),
                orden.getNumero(),
                orden.getEstado(),
                orden.getEstado().getDescripcion(),
                orden.getEstado().siguientesPosibles(),
                orden.puedeFacturarse(),
                orden.getEstado().permiteEditarLineas(),

                orden.getMoto().getId(),
                orden.getMoto().getMatricula(),
                orden.getMoto().descripcion(),
                orden.getCliente().getId(),
                orden.getCliente().nombreCompleto(),
                orden.getCliente().getTelefono(),
                orden.getCliente().tieneDatosFiscalesCompletos(),

                orden.getFechaEntrada(),
                orden.getFechaEstimadaSalida(),
                orden.getFechaRealSalida(),
                orden.getKmEntrada(),

                orden.getProblemaReportado(),
                orden.getDiagnostico(),
                orden.getTecnico() == null ? null : orden.getTecnico().getId(),
                orden.getTecnico() == null ? null : orden.getTecnico().getNombreCompleto(),

                orden.getTarifaHora(),
                orden.getTipoIva(),
                orden.getFechaPresupuesto(),
                orden.getFechaAprobacion(),
                orden.getAprobadoPor(),
                orden.getMotivoRechazo(),
                orden.getObservaciones(),

                bruto, descuento, base, iva, total, horas,

                lineas.stream().map(LineaOTResponse::de).toList(),
                historial.stream().map(CambioEstadoResponse::de).toList());
    }

    /**
     * La misma ficha con el dinero fuera: sin tarifa, sin totales y con las
     * lineas tambien limpias.
     *
     * <p>Es lo que ve un tecnico. Un taller puede querer que quien monta la moto
     * no sepa a cuanto se la cobra la casa al cliente —ni el precio de la hora ni
     * el de las piezas— y esa decision no puede quedarse en ocultar columnas en
     * la pantalla: la API devuelve JSON y cualquiera puede mirarlo. Por eso los
     * importes no salen del servidor, en vez de salir y no pintarse.
     *
     * <p>Se conserva {@code horasManoDeObra}: son horas de trabajo, no dinero, y
     * el tecnico necesita saber cuantas tiene apuntadas.
     */
    public OrdenTrabajoResponse sinImportes() {
        return new OrdenTrabajoResponse(
                id, codigo, ejercicio, numero, estado, estadoDescripcion, estadosPosibles,
                facturable, permiteEditarLineas,
                motoId, matricula, descripcionMoto, clienteId, clienteNombre, clienteTelefono,
                clienteFacturable,
                fechaEntrada, fechaEstimadaSalida, fechaRealSalida, kmEntrada,
                problemaReportado, diagnostico, tecnicoId, tecnicoNombre,
                // La tarifa/hora se va porque es un precio; el tipo de IVA se
                // queda porque no lo es: dice como se factura la orden, no a
                // cuanto se cobra.
                null, tipoIva, fechaPresupuesto, fechaAprobacion, aprobadoPor, motivoRechazo,
                observaciones,
                null, null, null, null, null, horasManoDeObra,
                lineas.stream().map(LineaOTResponse::sinImportes).toList(),
                historial);
    }

    private static BigDecimal sumar(List<LineaOT> lineas,
                                    java.util.function.Function<LineaOT, BigDecimal> campo) {
        return lineas.stream()
                .map(campo)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
