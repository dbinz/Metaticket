location / {
    alias /metaticket/portal.admin/;
    try_files $uri /index.html;
}