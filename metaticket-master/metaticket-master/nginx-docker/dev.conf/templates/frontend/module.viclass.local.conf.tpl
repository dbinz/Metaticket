# a path for all modules provided by viclass mfe
location /modules/viclass {
    add_header Access-Control-Allow-Origin *;
    alias /viclass/;
}
