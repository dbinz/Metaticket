location /modules/metaticket/themes/editor.seatplanner.ui {
    add_header Access-Control-Allow-Origin *;
    alias /metaticket/themes/;
}

location /modules/metaticket/editor.seatplanner.ui {
    add_header Access-Control-Allow-Origin *;
    alias /metaticket/mfe/;
}
