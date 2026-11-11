server {
    listen 443 ssl;
    listen [::]:443 ssl;

    server_name admin.devlocal.metaticket.vn;

    # SSL Configuration

    ssl_certificate     /etc/nginx/ssl/fullchain1.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey1.pem;
    ssl_protocols       TLSv1 TLSv1.1 TLSv1.2;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # ~~~ STATIC DEPLOYMENT ~~~
    # Pages like seat planner portal etc.. have their frontend code all distributed and
    # served at a certain folder (prod) or common dev server (dev) through Nginx

#    include conf.d/frontend/$CONF_ASSETS.conf;
    include conf.d/frontend/$CONF_PORTAL_SEATPLANNER.conf;
    # WordPress CMS on news.*; public news BFF + private admin BFF for portal
    include conf.d/frontend/$CONF_NEWS.conf;
    include conf.d/frontend/$CONF_NEWS_PRIVATE.conf;
    include conf.d/frontend/$CONF_PORTAL_ADMIN.conf;

    # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    # ~~~ API ~~~

    # Other services also expose some APIs so that from frontend / 3rd party website users
    # can access these API e.g. for create document, load document, modify document, etc...

    include conf.d/frontend/$CONF_EDITOR_POLICYDESC.conf;
    include conf.d/frontend/$CONF_EDITOR_SEATPLANNER.conf;
    include conf.d/frontend/$CONF_SHOW.conf;
    include conf.d/frontend/$CONF_TICKET.conf;
    include conf.d/frontend/$CONF_ORDER.conf;
    include conf.d/frontend/$CONF_PAYMENT.conf;
    include conf.d/frontend/$CONF_QRCODE.conf;
    include conf.d/frontend/$CONF_TICKETSLOT.conf;
    include conf.d/frontend/$CONF_NOTIFICATION.conf;
    include conf.d/frontend/$CONF_ADMIN_API.conf;
    include conf.d/frontend/$CONF_AUTH.conf;
    # Accessory admin catalog (list/CRUD) — must be on admin host so portal /private/accessory works
    include conf.d/frontend/$CONF_ACCESSORY.conf;

    # configuration service API

    # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    # ~~~ FEDERATED MODULES ~~~~
    # Editors, coordinators, and their UIs etc.. are component implementations loaded by the frontend
    # or 3rd party website users. These component implementations are wrapped inside webpack
    # federated module so that their dependencies can be shared among each other or the
    # host applications. The federated modules are distributed and deployed and served
    # through Nginx.

   include conf.d/frontend/$CONF_MODULE_VICLASS.conf;
   include conf.d/frontend/$CONF_MODULE_EDITOR_POLICYDESC.conf;
   include conf.d/frontend/$CONF_MODULE_EDITOR_SEATPLANNER.conf;
   include conf.d/frontend/$CONF_MODULE_EDITOR_SEATPLANNER_UI.conf;
   include conf.d/frontend/$CONF_MODULE_WW.conf;

    # ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
}

