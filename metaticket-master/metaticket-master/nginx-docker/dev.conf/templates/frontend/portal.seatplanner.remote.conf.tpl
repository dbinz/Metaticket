location /planner {
    proxy_pass http://$METATICKET_REMOTE;
}