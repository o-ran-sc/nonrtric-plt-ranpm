# DFC (DataFile Collector)

Datafile Collector is responsible for collecting PM counter files from PNF (Physical Network Function).
The files are stored in a persistent volume or in an S3 object store.

The origin is from ONAP. This variant uses Kafka and S3 object store and does not use the Dmaap.

## Introduction

DFC is delivered as one **Docker container** which hosts application server and can be started by `docker-compose`.

## Compiling DFC

Whole project (top level of DFC directory) and each module (sub module directory) can be compiled using
`mvn clean install` command.

## Build image 
```
mvn install docker:build
```

## Main API Endpoints

Running with dev-mode of DFC

- **Heartbeat**: http://<container_address>:8100/**heartbeat** or https://<container_address>:8443/**heartbeat**

- **Start DFC**: http://<container_address>:8100/**start** or https://<container_address>:8433/**start**

- **Stop DFC**: http://<container_address>:8100/**stopDatafile** or https://<container_address>:8433/**stopDatafile**



## License

Copyright (C) 2018-2019 NOKIA Intellectual Property, 2018-20123 Nordix Foundation. All rights reserved.
[License](http://www.apache.org/licenses/LICENSE-2.0)
