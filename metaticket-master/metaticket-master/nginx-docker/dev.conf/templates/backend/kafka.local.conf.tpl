server {
    listen $KAFKA_CONNECT_PORT;
    proxy_pass $METATICKET_LOCAL:$KAFKA_LISTEN_PORT;
}