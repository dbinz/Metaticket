location /api/admin {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/admin {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/admin {
    proxy_pass http://$METATICKET_REMOTE;
}