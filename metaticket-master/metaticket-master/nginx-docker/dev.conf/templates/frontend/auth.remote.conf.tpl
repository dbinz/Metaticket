# Host must be the remote site's server_name (not admin.devlocal) so staging
# nginx routes /api/* to the API vhost instead of a default/SPA server.
location /api/authentication {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
}

location /api/public/authentication {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Cookie $http_cookie;
}

location /private/authentication {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
}
