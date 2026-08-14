-- =====================================================================
-- V16 - Una sola factura ordinaria por orden de trabajo
-- =====================================================================
--
-- El servicio ya comprobaba que la orden no estuviera facturada antes de
-- emitir, pero esa comprobacion es un "mira y luego escribe": dos peticiones a
-- la vez leen las dos que no hay factura, y las dos la crean.
--
-- No es un caso rebuscado. Basta con que alguien haga doble clic en «Emitir
-- factura» porque la primera pulsacion tardo: salen dos facturas selladas de la
-- misma reparacion, las dos cobrables y ninguna borrable, porque el registro de
-- facturacion es inmutable. Corregirlo obliga a emitir una rectificativa.
--
-- Con el indice unico la carrera la pierde una de las dos transacciones y el
-- usuario recibe el mismo aviso de siempre («esta orden ya tiene la factura
-- ...»), que es lo que esperaba leer.
--
-- Es parcial a proposito: solo mira las ORDINARIA. Las rectificativas apuntan a
-- la misma orden y tienen que poder ser varias, porque una factura se puede
-- corregir mas de una vez.

-- Si el fallo ya se colo alguna vez, el indice no se puede crear. Antes de
-- reventar con el mensaje criptico de PostgreSQL se dice cuales son y que hacer:
-- una factura emitida no se borra, se rectifica.
DO $$
DECLARE
    v_duplicadas TEXT;
BEGIN
    SELECT string_agg(codigo, ', ') INTO v_duplicadas
    FROM (
        SELECT o.codigo
          FROM factura f
          JOIN orden_trabajo o ON o.id = f.orden_trabajo_id
         WHERE f.tipo = 'ORDINARIA'
         GROUP BY o.codigo
        HAVING count(*) > 1
    ) AS repetidas;

    IF v_duplicadas IS NOT NULL THEN
        RAISE EXCEPTION
            'Hay ordenes con mas de una factura ordinaria: %. Emita una rectificativa de las sobrantes y vuelva a arrancar; una factura emitida no se borra.',
            v_duplicadas;
    END IF;
END $$;

CREATE UNIQUE INDEX uq_factura_una_por_orden
    ON factura (orden_trabajo_id)
    WHERE tipo = 'ORDINARIA' AND orden_trabajo_id IS NOT NULL;

COMMENT ON INDEX uq_factura_una_por_orden IS
    'Una orden de trabajo no puede tener dos facturas ordinarias. Cierra la carrera del doble clic.';
