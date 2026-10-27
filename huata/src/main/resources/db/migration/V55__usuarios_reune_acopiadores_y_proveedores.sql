-- V55: una sola pantalla para las personas (pedido del cliente, 2026-10-24: "el cliente se pierde,
-- que en usuarios esten los acopiadores y los proveedores con filtros, y que solo ahi se creen").
--
-- La web reune en "Usuarios" (ruta /usuarios) tres pestanas: cuentas internas, acopiadores y
-- proveedores. Cada pestana conserva su vista y su permiso: quien solo tenia proveedores.read sigue
-- viendo el padron, ahora como pestana de Usuarios, y no ve las cuentas internas. Nadie gana ni
-- pierde acceso: solo cambia donde vive cada vista. Mismo contenido que CatalogoVistas.

UPDATE vistas
SET modulo_id   = (SELECT id FROM modulos WHERE codigo = 'PERSONAS_RUTAS'),
    nombre      = 'Usuarios',
    descripcion = 'Cuentas internas, roles y permisos.',
    orden       = 5,
    updated_at  = now(),
    updated_by  = 'migracion V55'
WHERE codigo = 'usuarios';

UPDATE vistas
SET ruta        = 'usuarios',
    descripcion = 'Padrón de proveedores de leche (pestaña de Usuarios).',
    orden       = 6,
    updated_at  = now(),
    updated_by  = 'migracion V55'
WHERE codigo = 'productores';

UPDATE vistas
SET ruta        = 'usuarios',
    descripcion = 'Personas que realizan el recojo (pestaña de Usuarios).',
    orden       = 7,
    updated_at  = now(),
    updated_by  = 'migracion V55'
WHERE codigo = 'acopiadores';
