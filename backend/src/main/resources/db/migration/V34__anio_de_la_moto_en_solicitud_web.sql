-- =====================================================================
-- V34 - Año de la moto en las solicitudes de la web
-- =====================================================================
-- El formulario de la web pregunta ahora el año de la moto (opcional). Sirve
-- para afinar el presupuesto y se propone al dar de alta la moto.
-- ---------------------------------------------------------------------
ALTER TABLE solicitud_web
    ADD COLUMN anio INTEGER,
    ADD CONSTRAINT ck_solicitud_web_anio CHECK (anio IS NULL OR anio BETWEEN 1885 AND 2100);
