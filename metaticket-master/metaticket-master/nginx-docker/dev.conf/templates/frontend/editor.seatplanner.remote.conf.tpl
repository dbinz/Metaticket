location /api/editor/seatplanner {
    proxy_pass http://$METATICKET_REMOTE;
}

location /api/public/editor/seatplanner {
    proxy_pass http://$METATICKET_REMOTE;
}

location /private/editor/seatplanner {
    proxy_pass http://$METATICKET_REMOTE;
}