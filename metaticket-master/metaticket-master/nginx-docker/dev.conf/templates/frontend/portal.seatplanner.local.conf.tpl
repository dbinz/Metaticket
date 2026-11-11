location /planner/ {
    alias /metaticket/portal.seatplanner/;
    try_files $uri /planner/index.html;
}
