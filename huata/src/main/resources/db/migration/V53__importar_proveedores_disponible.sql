-- V53: "Importar" (respaldo.write) ya hace algo: importar el padron de proveedores desde Excel
-- (ImportacionProveedoresController). V51 lo habia rotulado "(aun no disponible)"; ahora la etiqueta
-- dice exactamente lo que concede. La restauracion completa de la base NO se hace desde la app
-- (DESARROLLO.md 12: nunca reemplazo total); es un procedimiento de servidor, ver
-- docs/RESPALDO_Y_RESTAURACION.md.
UPDATE vista_acciones
SET etiqueta = 'Importar proveedores (Excel)'
WHERE permiso_id = (SELECT id FROM permisos WHERE codigo = 'respaldo.write');

UPDATE permisos
SET descripcion = 'Importar el padron de proveedores desde Excel (alta o actualizacion, nunca reemplazo)'
WHERE codigo = 'respaldo.write';
