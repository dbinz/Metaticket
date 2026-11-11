location /internal/jobrunr {
    proxy_pass http://$METATICKET_REMOTE;
}
