-- V58: las vistas se llaman como en el menu lateral (pedido del cliente, 2026-10-25: "roles y vistas,
-- que sea facil de ver y no se vea sobrecargado"). En Roles y vistas aparecian "Acopio" y "Llegada a
-- planta" mientras el menu dice "Recojos en ruta" y "Recepcion de rutas": la misma pantalla con dos
-- nombres. Solo cambia el nombre; codigo, ruta y permisos quedan igual. Mismo contenido que CatalogoVistas.

UPDATE vistas
SET nombre     = 'Recojos en ruta',
    updated_at = now(),
    updated_by = 'migracion V58'
WHERE codigo = 'recojo';

UPDATE vistas
SET nombre     = 'Recepción de rutas',
    updated_at = now(),
    updated_by = 'migracion V58'
WHERE codigo = 'conciliacion';
