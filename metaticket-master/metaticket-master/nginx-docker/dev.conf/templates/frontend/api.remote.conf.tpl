location /api {
    proxy_pass http://$METATICKET_REMOTE;
}
