# Bare WP paths (missing /news-admin) → redirect so admin links/categories work
location = /wp-login.php {
    return 301 /news-admin/wp-login.php$is_args$args;
}
location /wp-admin/ {
    return 301 /news-admin$request_uri;
}
location /wp-content/ {
    return 301 /news-admin$request_uri;
}
location /wp-includes/ {
    return 301 /news-admin$request_uri;
}
location /wp-json/ {
    return 301 /news-admin$request_uri;
}
location ~ ^/(edit\.php|edit-tags\.php|term\.php|post\.php|post-new\.php|upload\.php|media-new\.php|options-[a-z-]+\.php|admin\.php|admin-ajax\.php|admin-post\.php|profile\.php|revision\.php|async-upload\.php)$ {
    return 301 /news-admin/wp-admin$request_uri;
}

location /news-admin/ {
    proxy_pass http://$METATICKET_REMOTE:$WORDPRESS_LISTEN_PORT/;
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

    proxy_redirect ~^https://(?:admin|news)\.devlocal\.metaticket\.vn/(wp-admin|wp-login\.php|wp-json|wp-content|wp-includes)(.*)$ https://news.devlocal.metaticket.vn/news-admin/$1$2;
    proxy_redirect ~^http://(?:admin|news)\.devlocal\.metaticket\.vn/(wp-admin|wp-login\.php|wp-json|wp-content|wp-includes)(.*)$ https://news.devlocal.metaticket.vn/news-admin/$1$2;
    proxy_redirect ~^/(wp-admin|wp-login\.php|wp-json|wp-content|wp-includes)(.*)$ /news-admin/$1$2;
}

# Debug-only: do not proxy raw WP REST on edge (enumeration / draft probe surface)
location /api/public/wordpress/ {
    deny all;
}

location ~ ^/news-admin/wp-json/wp/v2/users {
    deny all;
}
