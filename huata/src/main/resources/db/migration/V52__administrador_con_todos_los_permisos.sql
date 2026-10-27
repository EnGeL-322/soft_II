-- V52: el rol ADMINISTRADOR pasa a tener TODOS los permisos del catalogo (decision del cliente,
-- 2026-10-20).
--
-- Hasta aqui el administrador no registraba recojos, analisis de calidad, pagos, lotes ni ventas
-- (separacion de funciones). En la practica eso hacia que asignarle "todo" no le diera acceso, y
-- cada administrador terminaba con esos permisos concedidos uno por uno como excepcion personal.
-- La auditoria sigue registrando quien hace cada escritura. Mismo conjunto que MatrizPermisos.

INSERT INTO public.rol_permisos (rol_id, permiso_id)
SELECT r.id, p.id
FROM public.roles r
CROSS JOIN public.permisos p
WHERE r.nombre = 'ADMINISTRADOR'
ON CONFLICT DO NOTHING;

UPDATE public.roles
SET descripcion = 'Administra el sistema con acceso total: configuracion, datos maestros y todas las'
                  || ' operaciones (acopio, calidad, pagos, produccion y ventas).',
    accesos = 'Todos los permisos'
WHERE nombre = 'ADMINISTRADOR';

-- Las excepciones CONCEDER que ahora ya da el rol no cambian nada y confundirian a quien lea la ficha
-- (mismo criterio que UsuarioService.aplicarExcepciones). Las REVOCAR se respetan: siguen quitando.
DELETE FROM public.usuario_permisos up
USING public.usuario_roles ur, public.roles r
WHERE up.usuario_id = ur.usuario_id
  AND ur.rol_id = r.id
  AND r.nombre = 'ADMINISTRADOR'
  AND up.tipo = 'CONCEDER';
