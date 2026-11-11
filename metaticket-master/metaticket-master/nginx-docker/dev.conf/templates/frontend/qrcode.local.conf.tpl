location /api/qrcode/ {
    proxy_pass http://$METATICKET_LOCAL:$QRCODE_BACKEND_LISTEN_PORT/qrcode/; # ending slash is important because it overwrites the /frd in the original url
}

location /api/public/qrcode/ {
    proxy_pass http://$METATICKET_LOCAL:$QRCODE_BACKEND_LISTEN_PORT/public/qrcode/; # ending slash is important because it overwrites the /frd in the original url
}

location /private/qrcode/ {
    proxy_pass http://$METATICKET_LOCAL:$QRCODE_BACKEND_LISTEN_PORT;
}