# Host must be the remote site's server_name (not devlocal) so staging nginx
# routes /api/* to the API vhost instead of the portal SPA default server.
location /api/customer-user {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
}

location /api/public/customer-user {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Cookie $http_cookie;
}

location /private/customer-user {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
}
