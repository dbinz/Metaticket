location /api/show/ {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/show/ {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/show {
    proxy_pass http://$METATICKET_REMOTE;
}