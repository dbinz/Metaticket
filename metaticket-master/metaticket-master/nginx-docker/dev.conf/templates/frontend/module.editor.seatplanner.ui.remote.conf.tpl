location /modules/metaticket/themes/editor.seatplanner.ui {
    proxy_pass http://$METATICKET_REMOTE;
}

location /modules/metaticket/editor.seatplanner.ui {
    proxy_pass http://$METATICKET_REMOTE;
}
