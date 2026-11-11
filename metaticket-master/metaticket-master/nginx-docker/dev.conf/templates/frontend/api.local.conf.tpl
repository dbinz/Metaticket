location /api {
    proxy_pass http://$METATICKET_LOCAL:3000;
}
