-- Verificacion ANTES de desplegar el cambio "ciclo de pago en hora de Lima".
--
-- Hasta ahora el ciclo jueves-miercoles se cortaba en UTC: lo entregado el miercoles entre las 19:00
-- y las 23:59 (hora de Lima) caia en la semana SIGUIENTE. Con el cambio cae en su propia semana.
-- Las liquidaciones ya CERRADAS no se recalculan, asi que un recojo de esa franja puede quedar:
--
--   a) ya pagado en una liquidacion cerrada (de esta semana o de la siguiente): no hay nada que hacer.
--   b) SIN liquidacion cerrada, y su semana (en hora de Lima) ya cerrada para ese proveedor:
--      el nuevo corte lo deja fuera de la semana siguiente y nadie lo pagaria. Hay que pagarlo con un
--      AJUSTE manual (Pagos -> liquidacion abierta del proveedor -> Ajuste "Bonificacion"), con
--      motivo "Transicion corte hora Lima, recojo <id>".
--
-- Solo lectura. Uso:
--   docker compose exec -T db psql -U "$DB_USER" -d "$DB_NAME" -f - < scripts/verificar_transicion_liquidaciones_lima.sql

WITH franja AS (
    SELECT r.id AS recojo_id,
           r.productor_id,
           r.litros,
           r.fecha_hora AT TIME ZONE 'America/Lima' AS hora_lima,
           -- jueves de inicio del ciclo al que pertenece en hora de Lima
           ((r.fecha_hora AT TIME ZONE 'America/Lima')::date
              - ((extract(isodow FROM r.fecha_hora AT TIME ZONE 'America/Lima')::int + 3) % 7)) AS ciclo_inicio_lima
    FROM recojos r
    WHERE r.estado <> 'CANCELLED'
      AND extract(isodow FROM r.fecha_hora AT TIME ZONE 'America/Lima') = 3      -- miercoles
      AND extract(hour   FROM r.fecha_hora AT TIME ZONE 'America/Lima') >= 19
)
SELECT f.recojo_id,
       p.codigo                                   AS proveedor,
       f.hora_lima,
       f.litros,
       f.ciclo_inicio_lima,
       EXISTS (SELECT 1
               FROM liquidacion_detalles d
               JOIN liquidaciones l ON l.id = d.liquidacion_id
               WHERE d.recojo_id = f.recojo_id AND d.activo AND l.activo AND l.estado = 'CLOSED')
                                                  AS ya_pagado,
       EXISTS (SELECT 1
               FROM liquidaciones l
               WHERE l.productor_id = f.productor_id AND l.activo AND l.estado = 'CLOSED'
                 AND l.periodo_inicio = f.ciclo_inicio_lima)
                                                  AS su_semana_esta_cerrada
FROM franja f
JOIN productores p ON p.id = f.productor_id
ORDER BY f.hora_lima;

-- Los que necesitan ajuste manual son las filas con ya_pagado = false y su_semana_esta_cerrada = true.
