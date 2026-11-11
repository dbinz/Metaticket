server {
    listen $REDIS_CONNECT_PORT;
    proxy_pass $METATICKET_LOCAL:$REDIS_LISTEN_PORT;
}