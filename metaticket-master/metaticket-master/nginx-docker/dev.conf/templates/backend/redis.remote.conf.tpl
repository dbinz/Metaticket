server {
    listen $REDIS_CONNECT_PORT;
    proxy_pass $METATICKET_REMOTE:$REDIS_LISTEN_PORT;
}