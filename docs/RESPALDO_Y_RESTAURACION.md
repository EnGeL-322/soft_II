# Respaldo y restauración — Huata Lácteos

Cubre RNF-09 ("se genera backup y se documenta/prueba un procedimiento de restauración") y RF-24/RF-46.
Hay dos cosas distintas, a propósito:

| Qué | Dónde | Para qué |
|---|---|---|
| **Respaldo / restauración completa** | Servidor, `scripts/` (fuera de la app) | Recuperar el sistema entero ante un fallo |
| **Importar Excel de proveedores** | Web → Proveedores → *Importar Excel* (permiso `respaldo.write` + `proveedores.write`) | Cargar o corregir el padrón; nunca reemplaza datos |

La app **no** reemplaza la base completa: DESARROLLO.md §12 lo prohíbe (con usuarios trabajando a la vez,
un reemplazo total desde el navegador borraría lo que otros registran). La restauración completa es una
operación de servidor, con el sistema detenido.

## 1. Hacer un respaldo

```bash
scripts/respaldo.sh            # deja los archivos en respaldos/
scripts/respaldo.sh /ruta/otra # o en otra carpeta
```

Genera, con la misma marca de fecha y hora:

- `huata_db_<marca>.dump` — la base PostgreSQL completa (`pg_dump -Fc`), incluido el historial de migraciones.
- `huata_almacen_<marca>.tar` — los archivos del almacén (fotos originales de voucher). La base solo guarda
  su referencia: sin este archivo, los vouchers restaurados quedarían sin foto.
- `huata_<marca>.sha256` — sumas para comprobar que los archivos no se dañaron al copiarlos.

No hace falta detener el sistema. **Copiar los tres archivos fuera del servidor** (otro disco o la nube):
un respaldo en el mismo disco no protege de la falla de ese disco. `respaldos/` no se versiona.

Programarlo (ejemplo, todos los días a las 23:30 en el servidor Linux):

```
30 23 * * * cd /opt/huata && scripts/respaldo.sh /mnt/respaldos >> /var/log/huata-respaldo.log 2>&1
```

## 2. Comprobar que un respaldo sirve (sin tocar nada)

```bash
scripts/restaurar.sh respaldos/huata_db_<marca>.dump
```

Comprueba las sumas, restaura en una base temporal (`huata_restauracion_prueba`), muestra las
migraciones y los conteos de las tablas principales junto a los de la base en uso, y borra la temporal.
Hacerlo al menos una vez al mes: un respaldo que nunca se probó restaurar no es un respaldo.

Probado el 2026-10-20 sobre la base local: restauró sin errores, migraciones 1…52, conteos idénticos
(productores 18, recojos 231, controles 226, liquidaciones 24, detalles 143, usuarios 30, rutas 5, evidencias 6).

## 3. Restaurar de verdad (reemplaza los datos en uso)

Solo ante una pérdida real. **Todo lo registrado después del respaldo se pierde.**

1. Avisar a los usuarios; los acopiadores pueden seguir trabajando sin señal, la app guarda y reenvía sola.
2. Respaldar el estado actual aunque esté dañado: `scripts/respaldo.sh respaldos/antes-de-restaurar`.
3. Restaurar:
   ```bash
   scripts/restaurar.sh respaldos/huata_db_<marca>.dump --confirmo-reemplazar
   ```
   Detiene el backend, reemplaza la base y el almacén (necesita el `.tar` con la misma marca al lado del
   `.dump`), y vuelve a arrancar. Flyway aplica al arrancar las migraciones posteriores al respaldo.
4. Comprobar: iniciar sesión, abrir Pagos y Calidad, ver un voucher.
5. Lo que las apps móviles tenían pendiente se reenvía solo; es idempotente (no se duplica).

## 4. Importar Excel de proveedores (desde la app)

- Columnas: `Codigo` (DNI, obligatorio), `Nombres`, `Apellidos`, `Ruta` (nombre de una ruta activa),
  `Telefono`, `Usuario`, `Clave_inicial`. Se ignoran las demás (`Zona`, `Estado`), así que **el Excel que
  exporta la misma pantalla se puede volver a importar tal cual**.
- Por cada fila: si el código existe, **actualiza** nombres, apellidos, teléfono y ruta; si no, lo **crea**
  en estado *Pendiente de aprobación* (necesita `Usuario` y `Clave_inicial`, igual que el alta manual).
  Nunca cambia estados ni borra proveedores.
- Siempre hay **vista previa** antes de guardar. Si alguna fila tiene error, **no se aplica ninguna**.
- Cada alta y modificación queda en la auditoría como si se hubiera hecho a mano.
- El archivo puede llevar claves iniciales: borrarlo después de importar.
