
## PM Data Producer

The latest documentation can be found here:
https://docs.o-ran-sc.org/projects/o-ran-sc-nonrtric-plt-ranpm/en/latest/pm-file-converter/index.html


The comonent is part of the RAM PM use case, which is described here:
https://docs.o-ran-sc.org/projects/o-ran-sc-nonrtric-plt-ranpm/en/latest/overview.html#overview

### Manual build, tag and push to image repo

Build for docker or local kubernetes\
`./build.sh no-push`

Build for remote kubernetes - an externally accessible image repo (e.g. docker hub) is needed  \
`./build.sh <external-image-repo>`

### Function

Producer supporting data types for pm xml to json conversion, pm json filtering with output to kafka or influx db.

### Configuration

The app expects the following environment variables:

- CREDS_GRANT_TYPE :  Grant type (keycloak)
- CREDS_CLIENT_SECRET: Client secret (keycloak)
- CREDS_CLIENT_ID : Client id (keycloak)
- AUTH_SERVICE_URL : Url to keycloak for fetching tokens
- KAFKA_SERVER : Host and port to kafka bootstrap server
- ICS : Host and port to the Information Coordination Service
- SELF: Host and port of this app

The following env vars are optional
FILES_VOLUME : Path to persistent file storage (optional)
FILESTORE_USER : Minio filestore user
FILESTORE_PWD : Minio filestore password
FILESTORE_SERVER: Host and port of the minio filestore
KP : Id of the app

The app can be configured to read file from a mounted file system or from a filestore server (minio).

Mounted files:
Configure ´FILES_VOLUME´ and leave var starting with FILESTORE empty.

Filestore:
Configure env var starting with FILESTORE and leave ´FILES_VOLUME´empty.



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
