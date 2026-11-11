location /api/public/accessory/ {
    proxy_pass http://$METATICKET_LOCAL:$ACCESSORY_BACKEND_LISTEN_PORT/public/accessory/; # ending slash is important because it overwrites the /api in the original url
}

# Authenticated storefront APIs (checkout, cancel, my-orders, pending). Gateway injects
# X-Customer-User-Id after JWT validation — mirrors staging /api + customer-user local.
location /api/accessory/ {
    auth_request /internal/customer-authenticate;
    auth_request_set $phone $upstream_http_x_customer_phone;
    auth_request_set $userId $upstream_http_x_customer_user_id;
    proxy_set_header X-Customer-Phone $phone;
    proxy_set_header X-Customer-User-Id $userId;
    proxy_pass http://$METATICKET_LOCAL:$ACCESSORY_BACKEND_LISTEN_PORT/accessory/; # ending slash is important because it overwrites the /api in the original url
}

location /private/accessory/ {
    proxy_pass http://$METATICKET_LOCAL:$ACCESSORY_BACKEND_LISTEN_PORT;
}

location /internal/accessory/ {
    proxy_pass http://$METATICKET_LOCAL:$ACCESSORY_BACKEND_LISTEN_PORT;
}
