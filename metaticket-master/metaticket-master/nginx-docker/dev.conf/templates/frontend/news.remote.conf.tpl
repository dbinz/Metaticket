# Public customer news BFF — rewrite strips /api so Spring sees /public/news/…
location /api/public/news/ {
    rewrite ^/api/public/news/(.*)$ /public/news/$1 break;
    proxy_pass http://$METATICKET_REMOTE:$NEWS_BACKEND_LISTEN_PORT;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
}
