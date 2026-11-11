location /api/ticket/ {
    proxy_pass http://$METATICKET_LOCAL:$TICKET_BACKEND_LISTEN_PORT/ticket/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/ticket/ {
    proxy_pass http://$METATICKET_LOCAL:$TICKET_BACKEND_LISTEN_PORT/public/ticket/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/ticket/ {
    proxy_pass http://$METATICKET_LOCAL:$TICKET_BACKEND_LISTEN_PORT;
}
