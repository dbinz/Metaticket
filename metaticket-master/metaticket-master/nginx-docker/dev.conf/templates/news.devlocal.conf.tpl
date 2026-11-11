server {
    listen 443 ssl;
    listen [::]:443 ssl;

    server_name news.devlocal.metaticket.vn;

    # SSL Configuration
    ssl_certificate     /etc/nginx/ssl/fullchain1.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey1.pem;
    ssl_protocols       TLSv1 TLSv1.1 TLSv1.2;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # WordPress only on this host (no public/private news BFF — those stay on admin/homepage)
    include conf.d/frontend/$CONF_WORDPRESS.conf;
}
