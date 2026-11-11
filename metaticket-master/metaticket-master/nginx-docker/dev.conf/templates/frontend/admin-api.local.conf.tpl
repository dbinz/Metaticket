location /api/admin/ {
    proxy_pass http://$METATICKET_LOCAL:$ADMIN_API_BACKEND_LISTEN_PORT/admin/; # ending slash is important because it overwrites the original url
}

location /api/public/admin/ {
    proxy_pass http://$METATICKET_LOCAL:$ADMIN_API_BACKEND_LISTEN_PORT/public/admin/; # ending slash is important because it overwrites the original url
}

location /private/admin/ {
    proxy_pass http://$METATICKET_LOCAL:$ADMIN_API_BACKEND_LISTEN_PORT;
}