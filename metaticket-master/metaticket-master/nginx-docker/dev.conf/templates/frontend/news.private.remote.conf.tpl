location /private/news/ {
    proxy_pass http://$METATICKET_REMOTE:$NEWS_BACKEND_LISTEN_PORT/private/news/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Authorization $http_authorization;
}
