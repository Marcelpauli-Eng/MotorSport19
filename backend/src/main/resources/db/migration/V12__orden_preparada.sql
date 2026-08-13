-- =====================================================================
-- V12 - Estado PREPARADA: el trabajo lo deja listo direccion, lo ejecuta el taller
-- =====================================================================
--
-- Hasta aqui toda OT tenia que recorrer el camino largo: diagnostico ->
-- presupuesto -> aprobacion del cliente. Eso es lo correcto cuando la moto
-- entra con una averia por determinar, pero no cuando el trabajo ya esta
-- hablado y cerrado con el cliente y lo unico que falta es que alguien lo haga.
--
-- PREPARADA es ese caso: la orden esta compuesta entera (conceptos, piezas y
-- precios), se le asigna un tecnico y queda esperando a que la empiece.
--
--   RECIBIDA ──→ PREPARADA ──→ EN_REPARACION ──→ LISTA ──→ ENTREGADA
--                    └──→ RECHAZADA
--
-- El check hay que rehacerlo entero: PostgreSQL no permite anadir un valor a
-- una restriccion CHECK existente.

ALTER TABLE orden_trabajo DROP CONSTRAINT ck_orden_estado;

ALTER TABLE orden_trabajo ADD CONSTRAINT ck_orden_estado CHECK (estado IN (
    'RECIBIDA', 'PREPARADA', 'EN_DIAGNOSTICO', 'PRESUPUESTADA', 'APROBADA',
    'EN_REPARACION', 'ESPERANDO_PIEZAS', 'LISTA', 'ENTREGADA', 'RECHAZADA'));

COMMENT ON COLUMN orden_trabajo.estado IS
    'Estado de la OT. PREPARADA = compuesta por direccion y a la espera de que el tecnico la empiece.';
