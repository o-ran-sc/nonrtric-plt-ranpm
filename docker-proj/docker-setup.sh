#!/bin/bash

#  ============LICENSE_START===============================================
#  Copyright (C) 2023 Nordix Foundation. All rights reserved.
#  Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
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

print_usage() {
    echo "Usage: docker-setup.sh"
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

./docker-tear-down.sh

echo "Docker pruning"
docker system prune -f
docker volume prune -f

echo "Creating dir for minio volume mapping"

mkdir -p /tmp/minio-test
mkdir -p /tmp/minio-test/0
rm -rf /tmp/minio-test/0/*

NW="nonrtric-docker-net"
echo "Creating docker network"
docker network inspect $NW 2> /dev/null 1> /dev/null
if [ $? -ne 0 ]; then
    docker network create $NW
else
    echo "  Network: $NW exits"
fi
}

pull_image() {
if [ -z "$(docker images -q $1)" ]; then
   echo "Pulling image... "$1
   docker pull $1
   check_error $?
fi
}

check_images(){
export KEYCLOAK_IMAGE=quay.io/keycloak/keycloak:20.0.1
pull_image $KEYCLOAK_IMAGE

export OPA_IMAGE=openpolicyagent/opa:0.70.0-envoy-17
pull_image $OPA_IMAGE

export BUNDLE_IMAGE=nginx:1.21
pull_image $BUNDLE_IMAGE

export MINIO_IMAGE=minio/minio
pull_image $MINIO_IMAGE

export REDPANDA_IMAGE=redpandadata/console:v2.2.3
pull_image $REDPANDA_IMAGE

export STRIMZI_IMAGE=quay.io/strimzi/kafka:0.35.0-kafka-3.4.0
pull_image $STRIMZI_IMAGE

export DMAAP_IMAGE=nexus3.onap.org:10002/onap/dmaap/dmaap-mr:1.4.4
pull_image $DMAAP_IMAGE

export VES_COLLECTOR_IMAGE=nexus3.onap.org:10002/onap/org.onap.dcaegen2.collectors.ves.vescollector:1.12.3
pull_image $VES_COLLECTOR_IMAGE

export ICS_IMAGE="nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-informationcoordinatorservice:1.5.0"
pull_image $ICS_IMAGE

export DMAAPADP_IMAGE="nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-pmproducer:1.0.1"
pull_image $DMAAPADP_IMAGE

export DFC_IMAGE="nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-ranpm-datafilecollector:1.0.0"
pull_image $DFC_IMAGE

export KPX_IMAGE="nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-ranpm-pm-file-converter:1.1.1"
pull_image $KPX_IMAGE

export AUTH_TOKEN_IMAGE=nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-plt-auth-token-fetch:1.1.1
pull_image $AUTH_TOKEN_IMAGE

export NONRTRIC_GATEWAY_IMAGE=nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-gateway:1.2.0
pull_image $NONRTRIC_GATEWAY_IMAGE

export CONTROL_PANEL_IMAGE=nexus3.o-ran-sc.org:10001/o-ran-sc/nonrtric-controlpanel:2.5.0
pull_image $CONTROL_PANEL_IMAGE
}

setup_keycloak() {
./config/keycloak/certs/gen-certs.sh
echo "Starting containers for: keycloak, opa"
envsubst  '$KEYCLOAK_IMAGE,$OPA_IMAGE,$BUNDLE_IMAGE' < docker-compose-security.yaml > docker-compose-security_gen.yaml
docker-compose -p security -f docker-compose-security_gen.yaml up -d
}

populate_keycloak(){
# Create realm in keycloak
. scripts/populate_keycloak.sh

create_realms nonrtric-realm
while [ $? -ne 0 ]; do
    create_realms nonrtric-realm
done

# Create client for admin calls
cid="console-setup"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

echo ""

cid="console-setup"
__get_admin_token
TOKEN=$(get_client_token nonrtric-realm $cid)

cid="kafka-producer-pm-xml2json"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export XML2JSON_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

cid="pm-producer-json2kafka"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export JSON2KAFKA_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

cid="dfc"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export DFC_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)
}

setup_kafka() {
echo "Starting containers for: kafka, zookeeper, kafka client, ics, minio"
envsubst  < docker-compose-k1.yaml > docker-compose-k1_gen.yaml
docker-compose -p common -f docker-compose-k1_gen.yaml up -d
}

create_topics() {
echo "Creating topics: $TOPICS, may take a while ..."
for t in $TOPICS; do
    retcode=1
    rt=43200000
    echo "Creating topic $t with retention $(($rt/1000)) seconds"
    while [ $retcode -ne 0 ]; do
        docker exec -it common-kafka-1-1 ./bin/kafka-topics.sh \
		--create --topic $t --config retention.ms=$rt  --bootstrap-server kafka-1:9092
        retcode=$?
    done
done
}

setup_dfc() {
export NUM_DFC=1
echo "Starting $NUM_DFC dfc"
export DFC_MINIO=http://minio-server:9000
export FILES_VOLUME="/pm-files"

cwd=$PWD
for (( i=1; i<=$NUM_DFC; i++ )); do
    echo "Updating dfc$i truststore"
    cd $cwd/config/dfc$i
    cp ../dfc-common/template-truststore.jks truststore.jks
    check_error $?

    echo " Adding https ca cert to dfc$i truststore"
    keytool -importcert -alias pm-https -file $cwd/config/https/certs/httpsca.crt -keystore truststore.jks -storetype JKS -storepass $(cat ../dfc-common/truststore.pass) -noprompt
    check_error $?
done
cd $cwd

chmod 666 config/dfc1/token-cache/jwt.txt
envsubst < docker-compose-dfc1.yaml > docker-compose-dfc_gen.yaml
envsubst < config/dfc1/application-template.yaml > config/dfc1/application.yaml
docker-compose -p dfc -f docker-compose-dfc_gen.yaml up -d
}

setup_producers() {
echo "Starting producers"
chmod 666 config/pmpr/token-cache/jwt.txt
export KPADP_MINIO=http://minio-server:9000
cp config/pmpr/application_configuration-minio-template.json config/pmpr/application_configuration.json
envsubst < config/pmpr/application-minio-template.yaml > config/pmpr/application.yaml

envsubst < docker-compose-producers.yaml > docker-compose-producers_gen.yaml
docker-compose -p prod -f docker-compose-producers_gen.yaml up -d
}

create_http_servers_certs() {
export NUM_HTTP=10
echo ""
./config/https/certs/gen-certs.sh $NUM_HTTP
}

setup_http_servers() {
cp pm-files/pm* ne-files

echo "Starting http servers"
export PM_HTTPSSERVER_IMAGE="pm-https-server:latest"

total_lines=$(cat docker-compose-pm-https.yaml | wc -l)
services_line=$(grep -n "services:" docker-compose-pm-https.yaml| cut -f1 -d:)
let remaining_lines=$total_lines-$services_line
export START_TIME=$(date +%Y%m%d.%H%M -d '3 hours ago')

grep -B $services_line "services:" docker-compose-pm-https.yaml > docker-compose-pm-https_gen.yaml
for (( i=1; i<=$NUM_HTTP; i++ )); do
   export CONTAINER_NUM=$i
   grep -A $remaining_lines "services:" docker-compose-pm-https.yaml | grep -v "services:" | \
   envsubst  '$CONTAINER_NUM,$PM_HTTPSSERVER_IMAGE,$START_TIME' >> docker-compose-pm-https_gen.yaml
done
docker-compose -p pm-https -f docker-compose-pm-https_gen.yaml up -d
}

## Main ##
export KAFKA_NUM_PARTITIONS=10
export TOPICS="file-ready collected-file json-file-ready-kp json-file-ready-kpadp pmreports"

setup_init

check_images

setup_keycloak
check_error $?

# Wait for keycloak to start
echo 'Waiting for keycloak to be ready'
until [ $(curl -s -w '%{http_code}' -o /dev/null 'http://localhost:8462') -eq 200 ];
do
	echo -n '.'
	sleep 2
done
echo ""
populate_keycloak

setup_kafka
check_error $?

create_topics

create_http_servers_certs
check_error $?

setup_dfc
check_error $?

setup_producers
check_error $?

setup_http_servers
check_error $?

scripts/clean-shared-volume.sh
