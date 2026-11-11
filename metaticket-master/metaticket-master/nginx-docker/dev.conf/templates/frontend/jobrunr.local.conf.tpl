location /internal/jobrunr/ {
    proxy_pass http://$METATICKET_LOCAL:$JOBRUNR_BACKEND_LISTEN_PORT;
}
