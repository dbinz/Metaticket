# Local: WordPress owns news.devlocal at host root (no /news-admin).
# Old bookmarks / portal links still using /news-admin → 301 to the same path at root.
location = /news-admin {
    return 301 /;
}
location /news-admin/ {
    rewrite ^/news-admin/?(.*)$ /$1 permanent;
}

location / {
    proxy_pass http://$METATICKET_LOCAL:$WORDPRESS_LISTEN_PORT;
    proxy_http_version 1.1;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port 443;
    proxy_read_timeout 120s;
    client_max_body_size 64m;

    proxy_hide_header X-Frame-Options;
    add_header Content-Security-Policy "frame-ancestors 'self' https://admin.devlocal.metaticket.vn" always;
}

# Debug-only: do not proxy raw WP REST on edge (enumeration / draft probe surface)
location /api/public/wordpress/ {
    deny all;
}

location ~ ^/wp-json/wp/v2/users {
    deny all;
}
