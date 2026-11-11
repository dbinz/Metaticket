location /modules/metaticket/editor.policydesc {
    add_header Access-Control-Allow-Origin *;
    alias /metaticket/mfe/;
}
