-- V54: cada operacion de venta es un permiso propio (pedido del cliente, 2026-10-20: "poder dar
-- permisos de todo, incluso para registrar o vender").
--
-- Hasta aqui quesos.write concedia a la vez registrar una venta, marcarla cobrada, anularla (que
-- devuelve unidades al stock) y registrar lotes. No habia forma de dar "solo vender" o "solo cobrar"
-- a alguien. Ahora:
--   ventas.registrar  POST  /quesos/ventas
--   ventas.cobrar     PATCH /quesos/ventas/{id}/cobrada
--   ventas.anular     POST  /quesos/ventas/{id}/anular
--   quesos.write      sigue: lotes y el registro historico de quesos (se muestra en Produccion).
--
-- Nadie gana ni pierde acceso con esta migracion: quien tenia quesos.write (por rol o como
-- excepcion personal) recibe los tres permisos nuevos con el mismo signo. Mismo contenido que
-- MatrizPermisos y CatalogoVistas.

INSERT INTO permisos (codigo, recurso, accion, descripcion) VALUES
  ('ventas.registrar', 'ventas', 'registrar', 'Registrar ventas de productos'),
  ('ventas.cobrar',    'ventas', 'cobrar',    'Marcar ventas como cobradas'),
  ('ventas.anular',    'ventas', 'anular',    'Anular ventas y devolver las unidades al stock')
ON CONFLICT (codigo) DO NOTHING;

UPDATE permisos SET descripcion = 'Registrar lotes de produccion de quesos'
WHERE codigo = 'quesos.write';

-- Roles: los que tenian quesos.write (estandar o ajustado por el cliente) conservan lo que hacian.
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT rp.rol_id, nuevo.id
FROM rol_permisos rp
JOIN permisos viejo ON viejo.id = rp.permiso_id AND viejo.codigo = 'quesos.write'
CROSS JOIN permisos nuevo
WHERE nuevo.codigo IN ('ventas.registrar', 'ventas.cobrar', 'ventas.anular')
ON CONFLICT DO NOTHING;

-- El administrador tiene todo el catalogo (V52), tambien lo nuevo.
INSERT INTO rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permisos p
WHERE r.nombre = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;

-- Excepciones por usuario: un quesos.write concedido o quitado a una persona se copia igual.
INSERT INTO usuario_permisos (usuario_id, permiso_id, tipo, activo, created_at, created_by)
SELECT up.usuario_id, nuevo.id, up.tipo, up.activo, now(), 'migracion V54'
FROM usuario_permisos up
JOIN permisos viejo ON viejo.id = up.permiso_id AND viejo.codigo = 'quesos.write'
CROSS JOIN permisos nuevo
WHERE nuevo.codigo IN ('ventas.registrar', 'ventas.cobrar', 'ventas.anular')
ON CONFLICT (usuario_id, permiso_id) DO NOTHING;

-- Pantalla de ventas: una accion por operacion. quesos.write pasa a la vista de Produccion.
DELETE FROM vista_acciones
WHERE vista_id = (SELECT id FROM vistas WHERE codigo = 'venta-queso')
  AND permiso_id = (SELECT id FROM permisos WHERE codigo = 'quesos.write');

INSERT INTO vista_acciones (vista_id, permiso_id, etiqueta, orden)
SELECT v.id, p.id, x.etiqueta, x.orden
FROM (VALUES
        ('venta-queso',      'ventas.registrar', 'Registrar ventas',         20),
        ('venta-queso',      'ventas.cobrar',    'Marcar como cobradas',     30),
        ('venta-queso',      'ventas.anular',    'Anular ventas',            40),
        ('produccion-queso', 'quesos.write',     'Registrar lotes de queso', 30)
     ) AS x(vista, permiso, etiqueta, orden)
JOIN vistas v ON v.codigo = x.vista
JOIN permisos p ON p.codigo = x.permiso
ON CONFLICT (vista_id, permiso_id) DO NOTHING;
