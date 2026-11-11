location /api/authentication {
    # ending slash is important because it overwrites the /authentication in the original url
    proxy_pass http://$METATICKET_LOCAL:$AUTH_BACKEND_LISTEN_PORT/authentication;
}

location /api/public/authentication {
    # ending slash is important because it overwrites the /authentication in the original url
    proxy_pass http://$METATICKET_LOCAL:$AUTH_BACKEND_LISTEN_PORT/public/authentication;
}

location /private/authentication {
    proxy_pass http://$METATICKET_LOCAL:$AUTH_BACKEND_LISTEN_PORT;
}