server {
    listen $MONGO_CONNECT_PORT;
    proxy_pass $METATICKET_LOCAL:$MONGO_LISTEN_PORT;
}