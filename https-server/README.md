
## Https server to simulate a RAN node for file download over https

### General

This server can be used to simulate a RAN node for file download over https.
Files can be requested in three ways:
- static file (always the same file returned)
- semi-static files (the requested file must exist in the container)
- generated files (file contents is generated using a template where the start/stop time as well the node name is based on requested file. Counter values are also generated)


### Build image

Build for docker or local kubernetes\
`./build.sh no-push`

Build for remote kubernetes - an externally accessible image repo (e.g. docker hub) is needed  \
`./build.sh <external-image-repo>`


### Configuration
The following env vars (all optional) may be set to control the behaviour of the server

- ALWAYS_RETURN - Name of a file under "/files" in the container that is always returned regardless of requested file on the url `/files/<file-id>`. The can be used when the file contents is not important.

- GENERATED_FILE_START_TIME - The first start date- and time stamp of file requested from the url `/generatedfiles/<file-id>`. Requesting a file with an earlier date and time will return http status 404. Example: "20230220.1300"

- GENERATED_FILE_TIMEZONE - Time zone to be used for requested files from the url `/generatedfiles/<file-id>`. Example: "+0100"

If generated files shall be used, load the file pm-template.xml.gz to the /template-files dir in the container.

Configure the following for desired behaviour
- static file: ALWAYS_RETURN
- semi-static files: none
- generated files: GENERATED_FILE_START_TIME and GENERATED_FILE_TIMEZONE



### API

| url | desc |
|--|--|
| `/files/{fileid}` | get static or semi-static file |
| `/generatedfiles/{fileid}` | get generated file |
| `/` | server alive check |


### File paths (container)


| path | desc |
|--|--|
| None |
| `/files` | Directory for static or semi-static files |
| `/template-files` | Directory of template file pm-template.xml.gz  |


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