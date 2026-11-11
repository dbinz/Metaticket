location /api/qrcode {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/qrcode {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/qrcode {
    proxy_pass http://$METATICKET_REMOTE;
}