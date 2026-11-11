SET curDir=%~dp0

START wt -d %curDir%\nginx-docker --title NGINX ; new-tab -d %curDir%\ts --title "LIBRARY" ; new-tab -d %curDir%\portal.admin --title "PORTAL" ; new-tab -d %curDir%\kotlin --title "BACKEND" ; focus-tab -t 1 ; split-pane -V -d %curDir%\ts --title "LIBRARY" ; mf left; split-pane -H -d %curDir%\ts --title "LIBRARY" ; mf up; split-pane -H -d %curDir%\ts --title "LIBRARY" ; mf down ; split-pane -H -d %curDir%\ts --title "LIBRARY" ; mf right; split-pane -H -d %curDir%\ts --title "LIBRARY" ; mf up; split-pane -H -d %curDir%\ts --title "LIBRARY" ; mf down; split-pane -H -d %curDir%\kotlin --title "EDITORS" ; focus-tab -t 2 ; split-pane -V -d %curDir%\frontend --title "PORTAL" ; mf left; split-pane -H -d %curDir%\ts --title "PORTAL" ; mf right; split-pane -H -d %curDir%\ts --title "PORTAL" ; focus-tab -t 3 ; split-pane -V -d %curDir%\kotlin --title "BACKEND" ; mf left ; split-pane -H -d %curDir%\kotlin --title "BACKEND" ; mf right ; split-pane -H -d %curDir%\kotlin --title "BACKEND" ; focus-tab -t 0


exit
