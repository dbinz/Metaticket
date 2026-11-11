location /modules/metaticket/editor.seatplanner {
    add_header Access-Control-Allow-Origin *;
    alias /metaticket/mfe/;
}
