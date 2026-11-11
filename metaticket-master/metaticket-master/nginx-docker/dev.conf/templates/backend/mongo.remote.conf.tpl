server {
    listen $MONGO_CONNECT_PORT;
    proxy_pass $METATICKET_REMOTE:$MONGO_LISTEN_PORT;
}