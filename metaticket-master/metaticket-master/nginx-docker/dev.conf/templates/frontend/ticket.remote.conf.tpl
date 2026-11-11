location /api/ticket {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/ticket {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/ticket {
    proxy_pass http://$METATICKET_REMOTE;
}