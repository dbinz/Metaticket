location /api/order {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/order {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/order {
    proxy_pass http://$METATICKET_REMOTE;
}

location /internal/order {
    proxy_pass http://$METATICKET_REMOTE;
}
