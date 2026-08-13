package com.motorsport19.taller.documento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * El historial de servicio de una moto, listo para imprimir.
 *
 * <p>No reutiliza {@link DocumentoImprimible} a proposito. Aquel es un
 * documento de venta —lineas con precio unitario, descuentos, base, IVA y una
 * banda de totales— y este es una hoja de vida: intervenciones ordenadas en el
 * tiempo, con sus kilometros y lo que se hizo en cada una. Forzarlos en el
 * mismo molde habria dejado media plantilla vacia en los dos.
 *
 * <p>Este papel se lo lleva el cliente cuando vende la moto, asi que se escribe
 * pensando en alguien que no ha pisado el taller: quien compra tiene que poder
 * leerlo solo.
 *
 * @param propietario  titular actual; el historial se queda con la moto cuando
 *                     cambia de manos, pero el papel lo pide quien la tiene hoy
 * @param conImportes  si se imprime lo que costo cada intervencion
 */
public record HistorialImprimible(
        Emisor emisor,
        Vehiculo vehiculo,
        String propietario,
        LocalDate fechaEmision,
        boolean conImportes,
        Resumen resumen,
        List<Intervencion> intervenciones
) {

    public record Emisor(String razonSocial, String direccion, String poblacion,
                         String nif, String telefono, String email) {
    }

    public record Vehiculo(String matricula, String marca, String modelo, Integer anio,
                           String bastidor, Integer kmActual) {
    }

    /**
     * Las cuatro cifras que miran primero quien compra la moto.
     *
     * @param kmRecorridos kilometros entre la primera y la ultima visita, o nulo
     *                     si no hay dos lecturas con las que restar
     */
    public record Resumen(
            int intervenciones,
            LocalDate primeraVisita,
            LocalDate ultimaVisita,
            Integer kmRecorridos,
            BigDecimal totalInvertido
    ) {
    }

    /**
     * Una entrada al taller.
     *
     * @param trabajos trabajos hechos, en el orden en que se apuntaron
     * @param piezas   material sustituido; va aparte de los trabajos porque es
     *                 lo primero que busca quien compra: si se cambio la correa,
     *                 los frenos o las ruedas
     */
    public record Intervencion(
            String codigo,
            LocalDate fecha,
            LocalDate fechaSalida,
            Integer km,
            String motivo,
            String diagnostico,
            String tecnico,
            List<String> trabajos,
            List<String> piezas,
            BigDecimal importe
    ) {
    }
}
