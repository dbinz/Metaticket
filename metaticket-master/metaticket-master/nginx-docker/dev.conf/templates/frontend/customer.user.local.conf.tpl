location /private/customer-user {
    # ending slash is important because it overwrites the /customer-user in the original url
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_USER_BACKEND_LISTEN_PORT/private/customer-user;
}

location /api/public/customer-user {
    # ending slash is important because it overwrites the /customer-user in the original url
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_USER_BACKEND_LISTEN_PORT/public/customer-user;
}

location /api/customer-user {
    auth_request /api/customer-auth/authenticate;
    auth_request_set $phone $upstream_http_x_customer_phone;
    auth_request_set $userId $upstream_http_x_customer_user_id;
    proxy_set_header X-Customer-Phone $phone;
    proxy_set_header X-Customer-User-Id $userId;
    proxy_pass http://$METATICKET_LOCAL:$CUSTOMER_USER_BACKEND_LISTEN_PORT/customer-user/profile;
}