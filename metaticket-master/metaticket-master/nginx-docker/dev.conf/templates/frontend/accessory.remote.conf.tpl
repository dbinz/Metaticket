location /api/public/accessory {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/accessory {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/accessory {
    proxy_pass http://$METATICKET_REMOTE;
}

location /internal/accessory {
    proxy_pass http://$METATICKET_REMOTE;
}
