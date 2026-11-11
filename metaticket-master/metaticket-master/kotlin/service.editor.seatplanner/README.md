# Intro

## Docker command

### Building

We first need the docker image

``` bash
docker build -f ./service.editor.seatplanner/deploy.Dockerfile -t metaticket/service.editor.seatplanner:1.0 ./service.editor.seatplanner
```

### Running

Run using host network to access localhost database.

**IMPORTANT** 
- The port mapping for different environment might be different. Adjust accordingly.
- The path to configuration might also be different. Adjust accordingly.

**STAGING**

``` bash
docker run --net=host -d --name service.editor.seatplanner-staging -p 4012:4012 -v /staging/metaticket/service/editor.seatplanner/conf:/service.editor.seatplanner/conf -v /staging/metaticket/service/editor.seatplanner/logs:/service.editor.seatplanner/logs metaticket/service.editor.seatplanner:1.0
```

**PRODUCTION**

``` bash
docker run --net=host -d --name service.editor.seatplanner-prod -p 3012:3012 -v /opt/metaticket/service/editor.seatplanner/conf:/service.editor.seatplanner/conf -v /opt/metaticket/service/editor.seatplanner/logs:/service.editor.seatplanner/logs metaticket/service.editor.seatplanner:1.0
```

If using swam, kubernetes or multiple container setup, networking configuration must be re-adjust.