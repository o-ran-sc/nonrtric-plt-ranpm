#!/bin/bash

#  ============LICENSE_START===============================================
#  Copyright (C) 2023 Nordix Foundation. All rights reserved.
#  ========================================================================
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#  ============LICENSE_END=================================================
#

. scripts/get_influxdb2_token.sh
. scripts/populate_keycloak.sh

print_usage() {
    echo "Usage: pmlog-setup.sh"
    exit 1
}

check_error() {
    if [ $1 -ne 0 ]; then
        echo "Failed $2"
        echo "Exiting..."
        exit 1
    fi
}

setup_init() {
echo "Cleaning previously started containers..."

./pmlog-tear-down.sh
}

pull_image() {
if [ -z "$(docker images -q $1)" ]; then
   echo "Pulling image... "$1
   docker pull $1
   check_error $?
fi
}

check_images(){
export INFLUXDB_IMAGE="influxdb:2.6.1"
pull_image $INFLUXDB_IMAGE

export PMLOG_IMAGE="nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-pmlog:1.0.0"
pull_image $PMLOG_IMAGE

export AUTH_TOKEN_IMAGE=nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-auth-token-fetch:1.1.1
pull_image $AUTH_TOKEN_IMAGE
}

setup_influx() {
data_dir=./config/influxdb2/data
mkdir -p $data_dir

export INFLUXDB2_INSTANCE=influxdb2-0
export INFLUXDB2_USERNAME=admin
export INFLUXDB2_PASSWORD=mySuP3rS3cr3tT0keN
export INFLUXDB2_ORG=est
export INFLUXDB2_BUCKET=pm-logg-bucket

envsubst < docker-compose-influxdb.yaml > docker-compose-influxdb_gen.yaml
docker-compose -p influx -f docker-compose-influxdb_gen.yaml up -d
}

setup_pmlog() {
chmod 666 config/pmlog/token-cache/jwt.txt

cid="nrt-pm-log"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export PMLOG_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)
envsubst < docker-compose-pmlog.yaml > docker-compose-pmlog_gen.yaml
docker-compose -p pmlog -f docker-compose-pmlog_gen.yaml up -d
}
## Main ##
setup_init

check_images

setup_influx
check_error $?

# Wait for influxdb2 to start
echo 'Waiting for influxdb2 to be ready'
until [ $(curl -s -w '%{http_code}' -o /dev/null 'http://localhost:8086/health') -eq 200 ];
do
        echo -n '.'
        sleep 1
done
echo ""

INFLUXDB2_TOKEN=$(get_influxdb2_token $INFLUXDB2_INSTANCE)
echo $INFLUXDB2_TOKEN
export INFLUXDB2_TOKEN

setup_pmlog
check_error $?
