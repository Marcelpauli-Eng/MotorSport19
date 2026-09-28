-- Registro de jornada de las dos últimas semanas para la demo del vídeo: turno partido de lunes a viernes.
-- Solo para la base de la demo (ms19video); la del taller no se toca.
INSERT INTO fichaje (usuario_id, inicio, fin, created_by)
SELECT u,
       ((d::date + t.ini) AT TIME ZONE 'Europe/Madrid') + (random() * 12)::int * interval '1 minute',
       ((d::date + t.fin) AT TIME ZONE 'Europe/Madrid') + (random() * 15)::int * interval '1 minute',
       u
FROM unnest(ARRAY[2, 3, 4]) AS u,
     generate_series(date_trunc('week', now())::date - 7, current_date - 1, interval '1 day') AS d,
     (VALUES (time '08:00', time '14:00'), (time '15:30', time '18:30')) AS t(ini, fin)
WHERE extract(isodow FROM d) < 6;
