set $internal_host devlocal.metaticket.vn;

location /api/show/ {
    proxy_set_header Host $internal_host;
    proxy_pass http://$METATICKET_LOCAL:$SHOW_BACKEND_LISTEN_PORT/show/; # ending slash is important because it overwrites the /api in the original url
}

location /api/public/show/ {
    proxy_set_header Host $internal_host;
    proxy_pass http://$METATICKET_LOCAL:$SHOW_BACKEND_LISTEN_PORT/public/show/; # ending slash is important because it overwrites the /api in the original url
}

location /private/show {
    proxy_set_header Host $internal_host;
    proxy_pass http://$METATICKET_LOCAL:$SHOW_BACKEND_LISTEN_PORT;
}