-- =====================================================================
-- Factura simplificada
--
-- Hasta ahora no se podia emitir una factura sin el documento y el
-- domicilio completos del cliente. Eso es mas estricto que la norma y
-- estorba en el caso mas corriente del taller: el particular que se lleva
-- la moto, no pide nada, y del que solo se tiene el nombre y el telefono.
-- El taller si necesita la factura, aunque solo sea para su contabilidad.
--
-- La factura simplificada existe justo para eso: identifica a quien la
-- emite, no a quien la recibe. Si el cliente la quiere para deducirse el
-- IVA hay que darle una completa, con sus datos.
--
-- Decisiones de esta migracion:
--
--   * El limite se guarda en la configuracion en vez de clavarlo en el
--     codigo. Depende del tipo de actividad y puede cambiar por ley, y
--     quien lo sabe es la gestoria del taller, no este programa.
--   * La marca va en la factura Y en la serie. Las simplificadas se llevan
--     en su propia serie para que el libro quede ordenado, y una factura
--     solo puede emitirse en la serie que le corresponde.
--   * No se toca el tipo de factura (ORDINARIA / RECTIFICATIVA): una
--     simplificada sigue siendo una factura ordinaria, solo que sin los
--     datos del destinatario. Meterla como un tipo mas obligaria a revisar
--     todas las reglas que hoy distinguen ordinaria de rectificativa.
-- =====================================================================

ALTER TABLE configuracion_taller
    ADD COLUMN limite_factura_simplificada NUMERIC(12, 2) NOT NULL DEFAULT 3000.00;

COMMENT ON COLUMN configuracion_taller.limite_factura_simplificada IS
    'Importe maximo con IVA de una factura simplificada. Lo confirma la gestoria.';

ALTER TABLE serie_factura
    ADD COLUMN simplificada BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE factura
    ADD COLUMN simplificada BOOLEAN NOT NULL DEFAULT false;

-- En una simplificada no hay NIF ni domicilio del destinatario. El nombre se
-- mantiene obligatorio: aunque la norma no lo exija, el taller siempre sabe a
-- quien le ha arreglado la moto, y una factura sin ningun nombre no hay forma
-- de casarla despues con su orden.
ALTER TABLE factura ALTER COLUMN receptor_nif        DROP NOT NULL;
ALTER TABLE factura ALTER COLUMN receptor_direccion  DROP NOT NULL;
ALTER TABLE factura ALTER COLUMN receptor_cp         DROP NOT NULL;
ALTER TABLE factura ALTER COLUMN receptor_ciudad     DROP NOT NULL;
ALTER TABLE factura ALTER COLUMN receptor_provincia  DROP NOT NULL;

-- Que puedan faltar no significa que sobren: solo pueden faltar aqui.
ALTER TABLE factura
    ADD CONSTRAINT ck_factura_receptor_completo CHECK (
        simplificada
        OR (receptor_nif IS NOT NULL
            AND receptor_direccion IS NOT NULL
            AND receptor_cp IS NOT NULL
            AND receptor_ciudad IS NOT NULL
            AND receptor_provincia IS NOT NULL));
