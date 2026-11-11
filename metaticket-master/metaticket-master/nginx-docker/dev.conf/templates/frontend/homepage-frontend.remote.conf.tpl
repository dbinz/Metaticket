location / {
    proxy_pass http://$METATICKET_REMOTE;
}

location /assets/homepage {
    proxy_pass http://$METATICKET_REMOTE;
}