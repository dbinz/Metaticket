location /api/payment/ {
    proxy_pass http://$METATICKET_LOCAL:$PAYMENT_BACKEND_LISTEN_PORT/payment/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/payment/ {
    proxy_pass http://$METATICKET_LOCAL:$PAYMENT_BACKEND_LISTEN_PORT/public/payment/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/payment/ {
    proxy_pass http://$METATICKET_LOCAL:$PAYMENT_BACKEND_LISTEN_PORT;
}