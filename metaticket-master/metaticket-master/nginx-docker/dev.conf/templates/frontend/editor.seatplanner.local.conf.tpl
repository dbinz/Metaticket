location /api/editor/seatplanner/ {
    proxy_pass http://$METATICKET_LOCAL:$EDITOR_SEATPLANNER_BACKEND_LISTEN_PORT/editor/seatplanner/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/editor/seatplanner/ {
    proxy_pass http://$METATICKET_LOCAL:$EDITOR_SEATPLANNER_BACKEND_LISTEN_PORT/public/editor/seatplanner/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/editor/seatplanner/ {
    proxy_pass http://$METATICKET_LOCAL:$EDITOR_SEATPLANNER_BACKEND_LISTEN_PORT;
}