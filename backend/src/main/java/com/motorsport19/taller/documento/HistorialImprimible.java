package com.motorsport19.taller.documento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Un historial de servicio listo para imprimir: el de una moto o el de un
 * cliente con todas las suyas.
 *
 * <p>Los dos documentos son el mismo papel con distinto alcance, asi que
 * comparten molde: una lista de bloques, uno por moto, cada uno con su ficha,
 * su resumen y sus intervenciones. El historial de una moto es simplemente el
 * caso de un bloque. Con dos modelos separados, el dia que se toque uno el otro
 * se queda atras.
 *
 * <p>No reutiliza {@link DocumentoImprimible} a proposito. Aquel es un
 * documento de venta —lineas con precio unitario, descuentos, base, IVA y una
 * banda de totales— y este es una hoja de vida: intervenciones ordenadas en el
 * tiempo, con sus kilometros y lo que se hizo en cada una.
 *
 * <p>Este papel se lo lleva el cliente cuando vende, asi que se escribe
 * pensando en alguien que no ha pisado el taller: quien compra tiene que poder
 * leerlo solo.
 *
 * @param titulo      el rotulo impreso, que dice de que historial se trata
 * @param cliente     ficha del titular; solo se imprime en el historial de un
 *                    cliente, donde es el sujeto del documento
 * @param conImportes si se imprime lo que costo cada intervencion
 * @param resumen     el acumulado de todo el documento, sumando sus motos
 */
public record HistorialImprimible(
        Emisor emisor,
        String titulo,
        Cliente cliente,
        LocalDate fechaEmision,
        boolean conImportes,
        Resumen resumen,
        List<BloqueMoto> motos
) {

    public record Emisor(String razonSocial, String direccion, String poblacion,
                         String nif, String telefono, String email) {
    }

    public record Cliente(String nombre, String documento, String telefono, String poblacion) {
    }

    /**
     * Una moto y todo lo que se le ha hecho.
     *
     * @param propietario titular actual de esta moto. En el historial de una
     *                    moto suelta va aqui, porque no hay ficha de cliente
     *                    arriba que lo diga
     */
    public record BloqueMoto(
            Vehiculo vehiculo,
            String propietario,
            Resumen resumen,
            List<Intervencion> intervenciones
    ) {
    }

    public record Vehiculo(String matricula, String marca, String modelo, Integer anio,
                           String bastidor, Integer kmActual) {
    }

    /**
     * Las cifras que se miran primero.
     *
     * @param motos        cuantas motos entran en el acumulado; en el historial
     *                     de una sola vale 1 y no se imprime
     * @param kmRecorridos kilometros entre la primera y la ultima visita, o nulo
     *                     si no hay dos lecturas con las que restar. En el
     *                     resumen de un cliente con varias motos no se calcula:
     *                     sumar los kilometros de motos distintas no significa
     *                     nada
     */
    public record Resumen(
            int motos,
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
