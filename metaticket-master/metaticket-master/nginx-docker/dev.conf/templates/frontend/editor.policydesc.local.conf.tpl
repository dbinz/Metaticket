location /api/editor/policydesc/ {
    proxy_pass http://$METATICKET_LOCAL:$EDITOR_POLICYDESC_BACKEND_LISTEN_PORT/editor/policydesc/; # ending slash is important because it overwrites the /xyz in the original url
}

location /api/public/editor/policydesc/ {
    proxy_pass http://$METATICKET_LOCAL:$EDITOR_POLICYDESC_BACKEND_LISTEN_PORT/public/editor/policydesc/; # ending slash is important because it overwrites the /xyz in the original url
}

location /private/editor/policydesc/ {
    proxy_pass http://$METATICKET_LOCAL:$EDITOR_POLICYDESC_BACKEND_LISTEN_PORT;
}