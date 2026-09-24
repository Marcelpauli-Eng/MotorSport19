-- Pasar una orden a presupuestada tiene permiso propio. Antes bastaba con
-- ORDENES_ESTADO, y cualquiera que moviera estados podia presupuestar.
-- Se concede a quien ya decidia sobre el presupuesto (aprobar/rechazar).
INSERT INTO rol_permiso (rol_id, permiso)
SELECT rol_id, 'ORDENES_PRESUPUESTAR'
FROM rol_permiso
WHERE permiso = 'ORDENES_APROBAR'
ON CONFLICT DO NOTHING;
