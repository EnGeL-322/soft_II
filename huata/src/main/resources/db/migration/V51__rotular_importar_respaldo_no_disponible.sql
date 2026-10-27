-- V51: "Importar y restaurar" (respaldo.write) se ofrecia en la matriz de accesos sin que exista
-- ninguna funcion de importacion: concederlo no daba nada y parecia que asignar permisos no
-- funcionaba. No se quita la accion (todo permiso debe poder verse y quitarse en la matriz; ver
-- AccesosDinamicosTests), solo se rotula para que quien administra sepa que aun no hace nada.
-- Cuando exista el endpoint, otra migracion le devuelve su etiqueta.
UPDATE vista_acciones
SET etiqueta = 'Importar y restaurar (aún no disponible)'
WHERE permiso_id = (SELECT id FROM permisos WHERE codigo = 'respaldo.write');
