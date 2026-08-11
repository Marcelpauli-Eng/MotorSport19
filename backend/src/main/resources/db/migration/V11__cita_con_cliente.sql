-- =====================================================================
-- V11 - La cita puede apuntar a un cliente ya dado de alta
--
-- Faltaba el caso mas comun despues del cliente nuevo: el de siempre que
-- llama trayendo OTRA moto, o una que todavia no tiene ficha. Hasta ahora la
-- cita solo entendia dos extremos —moto del sistema, o todo escrito a mano—,
-- asi que a un cliente conocido habia que volver a teclearle nombre y
-- telefono, que es justo lo que un sistema con su ficha no deberia pedir.
--
-- Quedan tres formas de identificar una cita, de mas a menos informacion:
--   1. Moto del sistema      -> el cliente sale de ella
--   2. Cliente del sistema   -> se describe la moto a mano
--   3. Ni lo uno ni lo otro  -> nombre y telefono a mano
-- =====================================================================

ALTER TABLE cita ADD COLUMN cliente_id BIGINT;

ALTER TABLE cita
    ADD CONSTRAINT fk_cita_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id);

CREATE INDEX ix_cita_cliente ON cita (cliente_id, fecha_hora DESC) WHERE cliente_id IS NOT NULL;

COMMENT ON COLUMN cita.cliente_id IS
    'Cliente ya dado de alta cuando la moto todavia no tiene ficha. Si hay moto, manda la suya.';


-- La regla de identificacion se relaja para admitir el caso nuevo: basta con
-- saber a quien llamar, y un cliente con ficha ya lleva su telefono dentro.
ALTER TABLE cita DROP CONSTRAINT ck_cita_identificacion;

ALTER TABLE cita ADD CONSTRAINT ck_cita_identificacion CHECK (
    moto_id IS NOT NULL
    OR cliente_id IS NOT NULL
    OR (contacto_nombre IS NOT NULL AND contacto_telefono IS NOT NULL));
