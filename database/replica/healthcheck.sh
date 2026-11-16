#!/bin/bash
export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"

running=$(mysql -h 127.0.0.1 -uroot -e "SHOW REPLICA STATUS\G" 2>/dev/null | grep -c "_Running: Yes")
[ "$running" -eq 2 ]
