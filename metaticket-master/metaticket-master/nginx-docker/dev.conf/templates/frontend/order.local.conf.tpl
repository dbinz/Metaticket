location /api/order/ {
    proxy_pass http://$METATICKET_LOCAL:$ORDER_BACKEND_LISTEN_PORT/order/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/order/ {
    proxy_pass http://$METATICKET_LOCAL:$ORDER_BACKEND_LISTEN_PORT/public/order/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/order/ {
    proxy_pass http://$METATICKET_LOCAL:$ORDER_BACKEND_LISTEN_PORT;
}

location /internal/order/ {
    proxy_pass http://$METATICKET_LOCAL:$ORDER_BACKEND_LISTEN_PORT;
}