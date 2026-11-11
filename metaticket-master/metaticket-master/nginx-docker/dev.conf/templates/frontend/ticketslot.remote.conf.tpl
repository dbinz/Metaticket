location /api/ticketslot {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/ticketslot {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/ticketslot {
    proxy_pass http://$METATICKET_REMOTE;
}