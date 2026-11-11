server {
    listen $SHOW_GRPC_CONNECT_PORT;
    proxy_pass $METATICKET_LOCAL:$SHOW_GRPC_LISTEN_PORT;
}
