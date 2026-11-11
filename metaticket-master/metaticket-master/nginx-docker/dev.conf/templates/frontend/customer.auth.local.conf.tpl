location = /internal/customer-authenticate {
    internal;
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_AUTH_BACKEND_LISTEN_PORT/internal/customer-auth/authenticate;
    proxy_pass_request_body off;
    proxy_set_header Content-Length "";
    proxy_set_header X-Original-URI $request_uri;
    proxy_set_header Cookie $http_cookie;
}

location /api/customer-auth {
    auth_request /internal/customer-authenticate;
    auth_request_set $phone $upstream_http_x_customer_phone;
    auth_request_set $userId $upstream_http_x_customer_user_id;
    proxy_set_header X-Customer-Phone $phone;
    proxy_set_header X-Customer-User-Id $userId;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    # ending slash is important because it overwrites the /customer/auth in the original url
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_AUTH_BACKEND_LISTEN_PORT/customer-auth;
}

location /api/public/customer-auth {
    # ending slash is important because it overwrites the /customer/auth in the original url
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_AUTH_BACKEND_LISTEN_PORT/public/customer-auth;
}

location /private/customer-auth {
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_AUTH_BACKEND_LISTEN_PORT;
}