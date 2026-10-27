#!/bin/bash
# Chay 1 lan khi replica khoi tao volume lan dau (docker-entrypoint-initdb.d):
#   1. Copy du lieu hien co tu primary (kem GTID da chay)
#   2. Noi replica vao primary theo GTID auto-position va bat dau replicate
#   3. Bat super_read_only de khong ai ghi nham vao replica
set -euo pipefail

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"
PRIMARY=(-h "$MYSQL_PRIMARY_HOST" -uroot)

echo "[replica-init] Doi primary '${MYSQL_PRIMARY_HOST}' san sang..."
for _ in $(seq 1 60); do
    if mysql "${PRIMARY[@]}" -e "SELECT 1" > /dev/null 2>&1; then
        break
    fi
    sleep 2
done
mysql "${PRIMARY[@]}" -e "SELECT 1" > /dev/null

# Xoa GTID do entrypoint tao ra tren replica, de nhan GTID_PURGED tu ban dump.
mysql -uroot -e "RESET BINARY LOGS AND GTIDS;"

echo "[replica-init] Copy du lieu '${MYSQL_DATABASE}' tu primary..."
mysqldump "${PRIMARY[@]}" \
    --databases "$MYSQL_DATABASE" \
    --single-transaction --set-gtid-purged=ON \
    --routines --triggers --events \
    --default-character-set=utf8mb4 \
  | mysql -uroot --default-character-set=utf8mb4

echo "[replica-init] Bat replication..."
mysql -uroot <<SQL
CHANGE REPLICATION SOURCE TO
    SOURCE_HOST = '${MYSQL_PRIMARY_HOST}',
    SOURCE_PORT = 3306,
    SOURCE_USER = '${MYSQL_REPL_USER}',
    SOURCE_PASSWORD = '${MYSQL_REPL_PASSWORD}',
    SOURCE_AUTO_POSITION = 1,
    GET_SOURCE_PUBLIC_KEY = 1;
START REPLICA;
SET PERSIST super_read_only = ON;
SQL

echo "[replica-init] Xong"
