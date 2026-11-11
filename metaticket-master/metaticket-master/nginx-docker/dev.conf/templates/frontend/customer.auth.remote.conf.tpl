# Host must be the remote site's server_name (not devlocal) so staging nginx
# routes /api/* to the API vhost instead of the portal SPA default server.

# Auth subrequest for hybrid local services (e.g. accessory.local).
# Do NOT proxy to staging's `/internal/customer-authenticate` — that location is
# `internal` on the remote site and returns 404 to this container.
# Dial remote customer-auth Ktor via the docker stream CONNECT port instead.
location = /internal/customer-authenticate {
    internal;
    proxy_pass http://127.0.0.1:$CUSTOMER_AUTH_BACKEND_CONNECT_PORT/internal/customer-auth/authenticate;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
    proxy_set_header X-Original-URI $request_uri;
}

location /api/customer-auth {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
}

location /api/public/customer-auth {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Cookie $http_cookie;
}

location /private/customer-auth {
    proxy_pass http://$METATICKET_REMOTE;
    proxy_set_header Host $METATICKET_REMOTE_HOST;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header Authorization $http_authorization;
    proxy_set_header Cookie $http_cookie;
}
