# directly use the angular web server for auto reloading instead of serving homepage through the yarn serve
location / {
    proxy_pass http://$METATICKET_LOCAL:4200/;        # proxy pass to the webpack serve server running on local
}

location /ng-cli-ws {
    proxy_pass http://$METATICKET_LOCAL:4200;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "Upgrade";
    proxy_set_header Host $host;
}
