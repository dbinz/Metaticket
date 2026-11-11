location /modules/metaticket/editor.seatplanner {
    proxy_pass http://$METATICKET_REMOTE;
}
