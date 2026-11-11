# Angular live-reload websocket — must stay open or ng serve reloads the tab in a loop
location /ng-cli-ws {
    proxy_pass http://$METATICKET_LOCAL:4200;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "Upgrade";
    proxy_set_header Host $host;
    proxy_read_timeout 86400s;
    proxy_send_timeout 86400s;
}

# News detail: canonical URL is /news/:slug.html (BFF full HTML).
# .html route → service.news; bare /news/:slug → 301 to .html.
location ~ ^/news/(?!category/)([^/]+)\.html/?$ {
    proxy_pass http://$METATICKET_LOCAL:$NEWS_BACKEND_LISTEN_PORT/public/news/render/$1;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_read_timeout 60s;
}

location ~ ^/news/(?!category/)([^/.]+)/?$ {
    return 301 $scheme://$host/news/$1.html;
}

location / {
#     alias /homepage/;
#     try_files $uri /index.html;
    proxy_pass http://$METATICKET_LOCAL:4200;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection $connection_upgrade;
    proxy_set_header Host $host;
    proxy_connect_timeout 5s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}

location /assets/homepage {
#     alias /homepage/assets/homepage/;
#     try_files $uri /index.html;
    proxy_pass http://$METATICKET_LOCAL:4200;
}
