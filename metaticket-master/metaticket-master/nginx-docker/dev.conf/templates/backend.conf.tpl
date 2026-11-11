stream {
    include conf.d/backend/$CONF_MONGO.conf;
    include conf.d/backend/$CONF_REDIS.conf;
    include conf.d/backend/$CONF_KAFKA.conf;

    # This is for other service to connect to in the backend
    include conf.d/backend/$CONF_EDITOR_POLICYDESC_BACKEND.conf;
    include conf.d/backend/$CONF_EDITOR_SEATPLANNER_BACKEND.conf;
    include conf.d/backend/$CONF_SHOW_BACKEND.conf;
    include conf.d/backend/$CONF_TICKET_BACKEND.conf;
    include conf.d/backend/$CONF_ORDER_BACKEND.conf;
    include conf.d/backend/$CONF_PAYMENT_BACKEND.conf;
    include conf.d/backend/$CONF_ACCESSORY_BACKEND.conf;
    include conf.d/backend/$CONF_QRCODE_BACKEND.conf;
    include conf.d/backend/$CONF_TICKETSLOT_BACKEND.conf;
    include conf.d/backend/$CONF_JOBRUNR_BACKEND.conf;
    include conf.d/backend/$CONF_NOTIFICATION_BACKEND.conf;
    include conf.d/backend/$CONF_ADMIN_API_BACKEND.conf;
    include conf.d/backend/$CONF_AUTH_BACKEND.conf;
    include conf.d/backend/$CONF_CUSTOMER_USER_BACKEND.conf;
    include conf.d/backend/$CONF_CUSTOMER_AUTH_BACKEND.conf;
    include conf.d/backend/$CONF_HOMEPAGE_API_BACKEND.conf;
    include conf.d/backend/$CONF_NEWS_BACKEND.conf;
    include conf.d/backend/$CONF_WORDPRESS_BACKEND.conf;

    # ~~~ gRPC (HTTP/2 over raw TCP stream) ~~~
    # Inter-service gRPC communication. Each service that hosts a gRPC server is proxied
    # here so that a locally-running service can reach its gRPC dependencies either on the
    # local machine (*.grpc.local) or on the remote dev server (*.grpc.remote).
    include conf.d/backend/$CONF_SHOW_GRPC.conf;
    include conf.d/backend/$CONF_TICKET_GRPC.conf;
    include conf.d/backend/$CONF_ORDER_GRPC.conf;
    include conf.d/backend/$CONF_PAYMENT_GRPC.conf;
    include conf.d/backend/$CONF_ACCESSORY_GRPC.conf;
    include conf.d/backend/$CONF_QRCODE_GRPC.conf;
    include conf.d/backend/$CONF_TICKETSLOT_GRPC.conf;
    include conf.d/backend/$CONF_JOBRUNR_GRPC.conf;
    include conf.d/backend/$CONF_NOTIFICATION_GRPC.conf;
    include conf.d/backend/$CONF_ADMIN_API_GRPC.conf;
    include conf.d/backend/$CONF_CUSTOMER_USER_GRPC.conf;
}
