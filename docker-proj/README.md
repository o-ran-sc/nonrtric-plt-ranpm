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

## RANPM project in docker

A docker compose project showing pm file flow from simulated network elements to filtered pm data.

## Run in docker-desktop - docker only

### Clone the ranpm repo:

`git clone https://gerrit.nordix.org/local/oransc/ranpm`

### Build Https server

`cd ranpm/https-server`

`./build.sh no-push`


### Start demo

WARNING: The setup scripts below will prune all unused docker volumes!

INFO: Temporary files for some containers will be stored in subdirs under `/tmp`

`cd ranpm/docker-proj`

There are several ways to start and run the demo, with file or minio file storage, single or multi instances of data-file collector and kafka as well as a choice of sftp, ftpes or https.
Additional configuration can be made in the setup script.

It could be a good idea to clean any other running containers in docker to avoid port and container name clashes.

Command usage: `docker-setup.sh`


Example cmd: \
`./docker-setup.sh`

Let the script finish.

If the script fails, make sure to clean the setup before attempting a new setup.

`./docker-tear-down.sh`

In addition, a `docker system prune` might be needed now and then.

### PM Influx Logger
Additionally you can install PM influx logger.

Example cmd: \
`./pmlog-setup.sh`

To remove run:
`./pmlog-tear-down.sh`

To update the filter edit and run:
`./update-pmlog.sh`

### PM rApp
Additionally you can install PM rApp.

Build PM rApp

`cd ranpm/pm-rapp`

`./build.sh no-push`

Install PM rApp

`cd ranpm/docker-proj`

Example cmd: \
`./pmrapp-setup.sh`

To remove run:
`./pmrapp-tear-down.sh`


### Tools for monitoring

Open browser to redpanda (kafka gui) - watch topics, messages etc\
browser: `localhost:8780`

Open brower to minio - available only if minio is given on the cmd line when starting the demo\
user: admin pwd: adminadmin\
browser: `localhost:9001`


### Push data - basic

File ready events can be pushed to the ves collector or pushed directly to the topic for file ready events (bypassing the ves collector).

Push to ves collector:

Usage: `push-to-ves-collector.sh <node-count> <num-of-events> <node-name-base> <file-extension> <num-servers>`

Parameters/
node-count - number of unique NEs\
num-of-events - number of events per NE\
node-name-base - NE name prefix\
file-extension - xml or xml.gz
num-servers - number of sftp/ftpes/https servers to simulate  NEs (10 is default)

Usage: `push-to-file-ready-topic.sh <node-count> <num-of-events> <node-name-base> <file-extension> <num-servers>`

Parameter: \
Same as `push-to-ves-collector.sh`

Once the events has been pushed the progress can be viewed in the monitoring tools described above.

If several sets of data shall be pushed, just change the parameter `<node-name-base>` to make the new files unique.


### Clean up

Run the script to remove all docker containers.

`./docker-tear-down.sh`

To also cleanup files.

`cd pm-file-flow-demo/scripts`

`./clean-shared-volume.sh`

