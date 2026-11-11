location /api/payment {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/payment {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/payment {
    proxy_pass http://$METATICKET_REMOTE;
}