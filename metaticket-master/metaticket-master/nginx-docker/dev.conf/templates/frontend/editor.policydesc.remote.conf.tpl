location /api/editor/policydesc {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/editor/policydesc {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/editor/policydesc {
    proxy_pass http://$METATICKET_REMOTE;
}