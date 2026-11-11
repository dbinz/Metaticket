# a path for all modules provided by viclass mfe
location /modules/viclass {
    proxy_pass http://$METATICKET_REMOTE;
}
