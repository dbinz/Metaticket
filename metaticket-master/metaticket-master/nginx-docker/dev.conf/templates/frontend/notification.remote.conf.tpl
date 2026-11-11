location /api/notification {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/notification {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/notification {
    proxy_pass http://$METATICKET_REMOTE;
}