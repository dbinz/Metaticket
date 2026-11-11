location /modules/metaticket/editor.policydesc {
    proxy_pass http://$METATICKET_REMOTE;
}
