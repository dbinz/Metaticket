location /assets {
    proxy_pass http://$METATICKET_REMOTE;
}
