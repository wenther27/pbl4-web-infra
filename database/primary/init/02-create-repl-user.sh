#!/bin/bash
# Chay 1 lan khi primary khoi tao volume lan dau (docker-entrypoint-initdb.d).
# Tao tai khoan de replica dang nhap vao primary va doc binlog.
set -euo pipefail

export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"

mysql -uroot <<SQL
CREATE USER IF NOT EXISTS '${MYSQL_REPL_USER}'@'%' IDENTIFIED BY '${MYSQL_REPL_PASSWORD}';
GRANT REPLICATION SLAVE ON *.* TO '${MYSQL_REPL_USER}'@'%';
SQL

echo "[primary-init] Da tao tai khoan replication '${MYSQL_REPL_USER}'"
