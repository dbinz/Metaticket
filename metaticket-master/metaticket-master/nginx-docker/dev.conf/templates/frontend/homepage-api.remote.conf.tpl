location /api/metasearch/ {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/metasearch/ {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/metasearch/ {
    proxy_pass http://$METATICKET_REMOTE;
}
