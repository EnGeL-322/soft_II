#!/usr/bin/env bash
# Restauracion de un respaldo hecho con scripts/respaldo.sh. Ver docs/RESPALDO_Y_RESTAURACION.md.
#
#   scripts/restaurar.sh <huata_db_MARCA.dump>
#       VERIFICA (por defecto, no toca nada en uso): restaura en una base temporal, compara los
#       conteos de las tablas principales con la base actual y borra la temporal.
#
#   scripts/restaurar.sh <huata_db_MARCA.dump> --confirmo-reemplazar
#       REEMPLAZA la base en uso y el almacen de archivos por el respaldo. Detiene el backend mientras
#       tanto. Todo lo registrado despues del respaldo se pierde: hacer antes un respaldo del estado
#       actual. Requiere el .tar del almacen con la misma marca, junto al .dump.
set -euo pipefail
cd "$(dirname "$0")/.."
# Git Bash (Windows) convierte "/data" en "C:/Program Files/Git/data" al pasarlo a docker; en Linux no hace nada.
export MSYS_NO_PATHCONV=1

set -a
# shellcheck disable=SC1091
. ./.env
set +a

DUMP="${1:?Indica el archivo .dump a restaurar}"
MODO="${2:-verificar}"
[ -f "$DUMP" ] || { echo "No existe $DUMP" >&2; exit 1; }

MARCA="$(basename "$DUMP" .dump)"; MARCA="${MARCA#huata_db_}"
DIR="$(dirname "$DUMP")"
SUMAS="$DIR/huata_$MARCA.sha256"
if [ -f "$SUMAS" ]; then
    echo "Comprobando sumas de verificacion..."
    ( cd "$DIR" && sha256sum -c "$(basename "$SUMAS")" )
fi

psql_en() { docker compose exec -T db psql -U "$DB_USER" -d "$1" -v ON_ERROR_STOP=1 -tA -c "$2"; }

TABLAS="productores recojos controles_calidad liquidaciones liquidacion_detalles usuarios rutas evidencias_calidad"
conteos() {
    for t in $TABLAS; do
        printf '%s=%s ' "$t" "$(psql_en "$1" "select count(*) from $t" 2>/dev/null || echo '-')"
    done
}

if [ "$MODO" = "verificar" ]; then
    TEMP="huata_restauracion_prueba"
    echo "Restaurando en la base temporal $TEMP..."
    psql_en postgres "drop database if exists $TEMP" >/dev/null
    psql_en postgres "create database $TEMP" >/dev/null
    docker compose exec -T db pg_restore -U "$DB_USER" -d "$TEMP" --no-owner --exit-on-error < "$DUMP"
    echo "Migraciones en el respaldo: $(psql_en "$TEMP" "select string_agg(version, ',' order by installed_rank) from flyway_schema_history where success")"
    echo "Respaldo : $(conteos "$TEMP")"
    echo "En uso   : $(conteos "$DB_NAME")"
    psql_en postgres "drop database $TEMP" >/dev/null
    echo "Restauracion verificada: el respaldo se restaura sin errores. Base temporal eliminada."
    exit 0
fi

if [ "$MODO" != "--confirmo-reemplazar" ]; then
    echo "Modo desconocido: $MODO (usa --confirmo-reemplazar para reemplazar la base en uso)" >&2
    exit 1
fi

TAR="$DIR/huata_almacen_$MARCA.tar"
[ -f "$TAR" ] || { echo "Falta $TAR (respaldo del almacen con la misma marca)" >&2; exit 1; }

echo "Deteniendo el backend..."
docker compose stop backend
echo "Reemplazando la base $DB_NAME..."
psql_en postgres "select pg_terminate_backend(pid) from pg_stat_activity where datname = '$DB_NAME' and pid <> pg_backend_pid()" >/dev/null
psql_en postgres "drop database $DB_NAME" >/dev/null
psql_en postgres "create database $DB_NAME" >/dev/null
docker compose exec -T db pg_restore -U "$DB_USER" -d "$DB_NAME" --no-owner --exit-on-error < "$DUMP"
echo "Reemplazando el almacen de archivos..."
docker compose stop almacen
docker run --rm -i --volumes-from huata-almacen --entrypoint sh postgres:17-alpine \
    -c 'rm -rf /data/* /data/.[!.]* 2>/dev/null; tar -C /data -xf -' < "$TAR"
docker compose start almacen
docker compose start backend
echo "Restaurado: $(conteos "$DB_NAME")"
