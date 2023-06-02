

## Prerequisites

The ranpm setup works on linux/MacOS or on windows via WSL using a local or remote kubernetes cluster.

- local kubectl
- kubernetes cluster
- local docker for building images

It is recommended to run the ranpm on a kubernetes cluster instead of local docker-desktop etc as the setup requires a fair amount of computer resouces.

# Requirement on kubernetes

The demo set can be run on local or remote kubernetes.
Kubectl must be configured to point to the applicable kubernetes instance.
Nodeports exposed by the kubernetes instance must be accessible by the local machine - basically the kubernetes control plane IP needs to be accessible from the local machine.

- Latest version of istio installed

# Other requirements
- helm3
- bash
- cmd 'envsubst' must be installed (check by cmd: 'type envsubst' )
- cmd 'jq' must be installed (check by cmd: 'type jq' )
- keytool
- openssl


## Before installation
The following images need to be built manually. If remote or multi node cluster is used, then an image repo needs to be available to push the built images to.
If external repo is used, use the same repo for all built images and configure the reponame in `helm/global-values.yaml` (the parameter value of extimagerepo shall have a trailing `/`)

Build the following images (build instruction in each dir)
- ranpm/https-server
- ranpm/pm-file-converter
- pm-rapp


## Installation

The installation is made by a few scripts.
The main part of the ranpm is installed by a single script. Then, additional parts can be added on top. All installations in kubernetes is made by helm charts.

The following scripts are provided for installing (install-nrt.sh mush be installed first):

- install-nrt.sh : Installs the main parts of the ranpm setup
- install-pm-log.sh : Installs the producer for influx db
- install-pm-influx-job.sh : Sets up an alternative job to produce data stored in influx db.
- install-pm-rapp.sh : Installs a rapp that subscribe and print out received data

## Unstallation

There is a corresponding uninstall script for each install script. However, it is enough to just run `uninstall-nrt.sh` and `uninstall-pm-rapp.sh´.

## Exposed ports to APIs
All exposed APIs on individual port numbers (nodeporta) on the address of the kubernetes control plane.

### Keycloak API
Keycloak API accessed via proxy (proxy is needed to make keycloak issue token with the internal address of keycloak).
- nodeport: 31784

### OPA rules bundle server
Server for posting updated OPA rules.
- nodeport: 32201

### Information coordinator Service
Direct access to ICS API.
-nodeports (http and https): 31823, 31824

### Ves-Collector
Direct access to the Ves-Collector
- nodeports (http and https): 31760, 31761

## Exposed ports to admin tools
As part of the ranpm installation, a number of admin tools are installed.
The tools are accessed via a browser on individual port numbers (nodeports) on the address of the kubernetes control plane.

### Keycload admin console
Admin tool for keycloak.
- nodeport : 31788
- user: admin
- password: admin

### Redpanda consule
With this tool the topics, consumer etc can be viewed.
- nodeport: 31767

### Minio web
Browser for minio filestore.
- nodeport: 31768
- user: admin
- password: adminadmin

### Influx db
Browser for influx db.
- nodeport: 31812
- user: admin
- password: mySuP3rS3cr3tT0keN


## License

Copyright (C) 2023 Nordix Foundation. All rights reserved.
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
