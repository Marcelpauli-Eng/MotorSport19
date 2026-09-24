-- =====================================================================
-- Tasa de reciclaje de neumaticos
-- =====================================================================
--
-- Al vender un neumatico hay que repercutir la tasa de gestion del
-- neumatico fuera de uso, y va como concepto aparte en la factura, no
-- sumada al precio. En el taller eso se traduce en una linea mas por cada
-- neumatico, y es justo la que se olvida: nadie echa de menos una linea
-- de un euro y medio hasta que la gestoria pregunta.
--
-- Decisiones:
--
--   * Se guarda QUE familia son los neumaticos y QUE pieza es la tasa, no
--     un importe. Asi el precio y el IVA de la tasa viven donde viven los
--     de cualquier otra pieza, y subirla es cambiar un precio del almacen
--     en vez de tocar el programa. Cuando cambie por ley, cambia sola.
--   * Las dos columnas admiten nulo: sin configurar, esto no existe. Un
--     taller que no vende neumaticos no tiene por que enterarse.
--   * La familia es texto y no una clave ajena porque las familias de
--     pieza ya son texto libre en toda la aplicacion.
-- =====================================================================

ALTER TABLE configuracion_taller
    ADD COLUMN familia_neumaticos        VARCHAR(60),
    ADD COLUMN pieza_tasa_neumatico_id   BIGINT;

ALTER TABLE configuracion_taller
    ADD CONSTRAINT fk_configuracion_tasa_neumatico
        FOREIGN KEY (pieza_tasa_neumatico_id) REFERENCES pieza (id);

COMMENT ON COLUMN configuracion_taller.familia_neumaticos IS
    'Familia de pieza que se considera neumatico. Nulo: la tasa esta desactivada.';
COMMENT ON COLUMN configuracion_taller.pieza_tasa_neumatico_id IS
    'Pieza que se cobra como tasa de reciclaje por cada neumatico.';
