location /modules/metaticket/ww {
    proxy_pass http://$METATICKET_REMOTE;
}
