-- V60: dias que dura cada queso desde que se produce (2026-10-27).
--
-- Desde ese dia todo lote nace con vencimiento y un producto sin dias no se puede producir. Los
-- tres quesos del catalogo no tenian el dato; se les pone uno de partida (decidido con el equipo,
-- en refrigeracion) para que la planta no quede bloqueada. Se cambian cuando haga falta en
-- Configuracion -> Precios y calidad -> Vencimiento de productos.
--
-- Solo donde no hay dato: si alguien ya lo configuro, se respeta.
--   Queso fresco           7 dias   (alta humedad, sin maduracion)
--   Queso paria            15 dias  (semiduro, prensado)
--   Queso andino madurado  60 dias  (madurado, baja humedad)

UPDATE productos SET vida_util_dias = 7,  updated_at = now(), updated_by = 'migracion V60'
WHERE lower(nombre) = 'queso fresco' AND vida_util_dias IS NULL;

UPDATE productos SET vida_util_dias = 15, updated_at = now(), updated_by = 'migracion V60'
WHERE lower(nombre) = 'queso paria' AND vida_util_dias IS NULL;

UPDATE productos SET vida_util_dias = 60, updated_at = now(), updated_by = 'migracion V60'
WHERE lower(nombre) = 'queso andino madurado' AND vida_util_dias IS NULL;
