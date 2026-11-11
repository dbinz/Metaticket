location /api/notification/ {
    proxy_pass http://$METATICKET_LOCAL:$NOTIFICATION_BACKEND_LISTEN_PORT/notification/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/notification/ {
    proxy_pass http://$METATICKET_LOCAL:$NOTIFICATION_BACKEND_LISTEN_PORT/public/notification/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/notification/ {
    proxy_pass http://$METATICKET_LOCAL:$NOTIFICATION_BACKEND_LISTEN_PORT;
}