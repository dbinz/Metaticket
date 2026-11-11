location /api/metasearch/ {
    proxy_pass http://$METATICKET_LOCAL:$HOMEPAGE_API_BACKEND_LISTEN_PORT/metasearch/; # ending slash is important because it overwrites the /api in the original url
}

location /api/public/metasearch/ {
    proxy_pass http://$METATICKET_LOCAL:$HOMEPAGE_API_BACKEND_LISTEN_PORT/public/metasearch/; # ending slash is important because it overwrites the /api in the original url
}

location /private/metasearch/ {
    proxy_pass http://$METATICKET_LOCAL:$HOMEPAGE_API_BACKEND_LISTEN_PORT;
}
