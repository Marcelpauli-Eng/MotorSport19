package com.motorsport19.taller.factura.service;

/**
 * Una anomalia detectada al verificar el registro de facturacion.
 *
 * @param numeroRegistro posicion en la cadena donde se detecto
 * @param numeroFactura  numero visible de la factura afectada
 * @param tipo           que clase de problema es
 * @param detalle        explicacion concreta, con los valores implicados
 */
public record AnomaliaCadena(
        Long numeroRegistro,
        String numeroFactura,
        Tipo tipo,
        String detalle
) {

    public enum Tipo {
        /** La huella guardada no corresponde a la cadena canonica sellada. */
        HUELLA_ALTERADA("Huella alterada"),
        /**
         * Los datos de la factura han cambiado despues de sellarla: el importe,
         * la fecha o el numero ya no son los que se pasaron por SHA-256.
         */
        CONTENIDO_ALTERADO("Contenido alterado tras la emision"),
        /** La factura no enlaza con la huella de la anterior. */
        CADENA_ROTA("Cadena rota"),
        /** Falta una posicion en el registro: se ha borrado una factura. */
        HUECO_EN_EL_REGISTRO("Hueco en el registro"),
        /** Los totales no cuadran con las lineas o con el desglose. */
        TOTALES_DESCUADRADOS("Totales descuadrados"),
        /** La numeracion de la serie salta un numero. */
        NUMERACION_NO_CORRELATIVA("Numeracion no correlativa");

        private final String descripcion;

        Tipo(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }
}
