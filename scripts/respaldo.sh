#!/usr/bin/env bash
# Respaldo completo de Huata Lacteos (RNF-09): base PostgreSQL + archivos del almacen (fotos de
# voucher, que la base solo referencia). Ver docs/RESPALDO_Y_RESTAURACION.md.
#
#   scripts/respaldo.sh [carpeta_destino]        (por defecto: respaldos/)
#
# Genera, con la misma marca de tiempo:
#   huata_db_<marca>.dump        pg_dump en formato custom (se restaura con pg_restore)
#   huata_almacen_<marca>.tar    contenido del volumen del almacen de archivos
#   huata_<marca>.sha256         sumas de verificacion de ambos
#
# No detiene el sistema: pg_dump toma una foto consistente de la base aunque haya escrituras.
set -euo pipefail
cd "$(dirname "$0")/.."
# Git Bash (Windows) convierte "/data" en "C:/Program Files/Git/data" al pasarlo a docker; en Linux no hace nada.
export MSYS_NO_PATHCONV=1

set -a
# shellcheck disable=SC1091
. ./.env
set +a

DESTINO="${1:-respaldos}"
MARCA="$(date +%Y%m%d_%H%M%S)"
mkdir -p "$DESTINO"
DB_ARCHIVO="$DESTINO/huata_db_$MARCA.dump"
ALMACEN_ARCHIVO="$DESTINO/huata_almacen_$MARCA.tar"

echo "Respaldando la base $DB_NAME..."
docker compose exec -T db pg_dump -U "$DB_USER" -d "$DB_NAME" -Fc --no-owner > "$DB_ARCHIVO"

echo "Respaldando el almacen de archivos (vouchers)..."
# La imagen del almacen no trae tar: se lee su volumen desde un contenedor auxiliar de solo lectura.
docker run --rm --volumes-from huata-almacen:ro --entrypoint tar postgres:17-alpine -C /data -cf - . \
    > "$ALMACEN_ARCHIVO"

( cd "$DESTINO" && sha256sum "$(basename "$DB_ARCHIVO")" "$(basename "$ALMACEN_ARCHIVO")" ) \
    > "$DESTINO/huata_$MARCA.sha256"

echo "Listo:"
ls -lh "$DB_ARCHIVO" "$ALMACEN_ARCHIVO" "$DESTINO/huata_$MARCA.sha256"
echo "Verifica que se puede restaurar:  scripts/restaurar.sh $DB_ARCHIVO"
