location /api/ticketslot/ {
    proxy_pass http://$METATICKET_LOCAL:$TICKETSLOT_BACKEND_LISTEN_PORT/ticketslot/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/ticketslot/ {
    proxy_pass http://$METATICKET_LOCAL:$TICKETSLOT_BACKEND_LISTEN_PORT/public/ticketslot/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/ticketslot/ {
    proxy_pass http://$METATICKET_LOCAL:$TICKETSLOT_BACKEND_LISTEN_PORT;
}