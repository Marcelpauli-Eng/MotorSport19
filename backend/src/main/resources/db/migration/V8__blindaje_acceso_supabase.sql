-- =====================================================================
-- V8 - Blindaje del acceso directo a las tablas (relevante en Supabase)
-- =====================================================================
-- Supabase publica automaticamente TODA tabla del esquema `public` a traves
-- de PostgREST. Con la clave `anon` (que es publica por diseno y viaja en el
-- navegador) cualquiera podria leer y escribir directamente en las tablas,
-- saltandose por completo la capa de dominio de la aplicacion.
--
-- Eso no solo seria una fuga de datos de clientes: dejaria abierto un camino
-- para insertar facturas o mover stock sin pasar por las reglas de negocio.
-- Los triggers de V6 seguirian protegiendo la inmutabilidad y el stock, pero
-- nada impediria, por ejemplo, dar de alta clientes basura o leer el listado
-- completo de facturas.
--
-- Aqui se cierra ese camino de dos formas independientes:
--   1. RLS activado sin ninguna politica  -> deniega todo por defecto.
--   2. Revocacion explicita de permisos a los roles `anon` y `authenticated`.
--
-- La aplicacion Spring Boot no se ve afectada: conecta por JDBC con el rol
-- `postgres`, que es el propietario de las tablas y no pasa por RLS.
--
-- En un PostgreSQL normal (docker-compose, local, tests) los roles de Supabase
-- no existen y el bloque simplemente no hace nada.
-- =====================================================================


CREATE OR REPLACE FUNCTION fn_blindar_acceso_directo() RETURNS void
LANGUAGE plpgsql AS $$
DECLARE
    r RECORD;
    v_hay_supabase BOOLEAN;
BEGIN
    SELECT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') INTO v_hay_supabase;

    IF NOT v_hay_supabase THEN
        RAISE NOTICE 'Roles de Supabase no encontrados: se omite el blindaje (entorno PostgreSQL estandar).';
        RETURN;
    END IF;

    -- 1. RLS sin politicas: denegacion por defecto para cualquier rol que no
    --    sea el propietario de la tabla.
    FOR r IN
        SELECT tablename FROM pg_tables
         WHERE schemaname = 'public'
           AND tablename <> 'flyway_schema_history'
    LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', r.tablename);
        EXECUTE format('ALTER TABLE public.%I FORCE ROW LEVEL SECURITY', r.tablename);
    END LOOP;

    -- 2. Revocacion de permisos. Redundante con lo anterior a proposito: si
    --    manana alguien anade una politica RLS por error, los GRANT siguen sin
    --    existir y el acceso sigue cerrado.
    REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM anon, authenticated;
    REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM anon, authenticated;
    REVOKE ALL ON ALL FUNCTIONS IN SCHEMA public FROM anon, authenticated;
    -- Quita el USAGE que Supabase concede explicitamente a estos roles. Ojo:
    -- has_schema_privilege() seguira diciendo que si, porque el esquema `public`
    -- concede USAGE al pseudo-rol PUBLIC y eso no se toca (romperia el propio
    -- Supabase). No importa: sin privilegios sobre las tablas, el USAGE del
    -- esquema por si solo no permite leer ni escribir nada.
    REVOKE USAGE ON SCHEMA public FROM anon, authenticated;

    -- Y lo mismo para lo que se cree en el futuro.
    ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES    FROM anon, authenticated;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM anon, authenticated;
    ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON FUNCTIONS FROM anon, authenticated;

    RAISE NOTICE 'Blindaje aplicado: RLS activo y permisos revocados para anon y authenticated.';
END;
$$;

COMMENT ON FUNCTION fn_blindar_acceso_directo() IS
    'Cierra el acceso directo a las tablas desde la API de Supabase. Idempotente: reejecutar tras crear tablas nuevas.';

SELECT fn_blindar_acceso_directo();
