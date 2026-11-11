server {
    listen $KAFKA_CONNECT_PORT;
    proxy_pass $METATICKET_REMOTE:$KAFKA_LISTEN_PORT;
}