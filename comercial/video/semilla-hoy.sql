-- Mostrador y los dos técnicos ya han fichado esta mañana (para el control de horas del vídeo de dirección).
INSERT INTO fichaje (usuario_id, inicio, created_by)
SELECT u, (current_date + time '08:00' + (u * 3) * interval '1 minute') AT TIME ZONE 'Europe/Madrid', u
FROM unnest(ARRAY[2, 3, 4]) AS u;
