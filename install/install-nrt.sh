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

. scripts/kube_get_controlplane_host.sh
. scripts/kube_get_nodeport.sh
. scripts/wait_for_server_ok.sh
. scripts/get_influxdb2_token.sh
. scripts/create_topic.sh

# Constants
SAMELINE="\033[0K\r"

# Variables
export KHOST=$(kube_get_controlplane_host)
if [ $? -ne 0 ]; then
    echo $KHOST
    echo "Exiting"
    exit 1
fi

echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
echo "Kubernetes control plane host: $KHOST"
echo "Host obtained from current kubectl context"
echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
echo "Restarting istiod, workaround to refresh jwks cache"
kubectl rollout restart deployments/istiod -n istio-system
echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

# Generic error printout function
# args: <numeric-response-code> <descriptive-string>
check_error() {
    if [ $1 -ne 0 ]; then
        echo "Failed: $2"
        echo "Exiting..."
        exit 1
    fi
}

##################################################################################
echo "##### Installing chart: namespaces"
##################################################################################

helm install --wait namespaces helm/namespaces

echo ""

##################################################################################
echo "##### Installing chart: nrt-base-0"
##################################################################################
helm install --wait -n nonrtric nrt-base-0 helm/nrt-base-0

# Create realm in keycloak

##export KC_PORT=$(kube_get_nodeport keycloak nonrtric http)
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

# retcode=0
# while [ $retcode -eq 0 ]; do
#     #NRT_REALM_JWKS=$(kubectl exec -n nonrtric client -- curl -f http://keycloak.nonrtric:8080/realms/nonrtric-realm/protocol/openid-connect/certs)
#     NRT_REALM_JWKS=$(curl -fs localhost:31788/realms/nonrtric-realm/protocol/openid-connect/certs)
#     if [ $? -eq 0 ]; then
#         retcode=1
#         #echo $NRT_REALM_JWKS
#         echo "JWKS for nonrtric-realm obtained"
#     else
#         sleep 3
#         echo "Wating for keycloak to publish JWKS for nonrtric-realm"
#     fi
# done

# export NRT_REALM_JWKS

echo ""

# ##################################################################################
# echo "##### Installing chart httpecho"
# ##################################################################################

#helm install --wait -n nonrtric httpecho helm/httpecho



# TSEC=$SECONDS
# numok=0
# while [ $numok -lt 10 ]; do
#     echo ""
#     echo "Time: $(($SECONDS-$TSEC))"
#     cid="console-setup"
#     __get_admin_token
#     TOKEN=$(get_client_token nonrtric-realm $cid)
#     decode_token "$TOKEN"
#     curl -fv localhost:31789/ -H "Authorization: Bearer $TOKEN"
#     if [ $? -eq 0 ]; then
#         let numok=numok+1
#     fi
#     sleep 5
# done

# TSEC=$SECONDS
# while [ true ]; do
#     echo ""
#     echo "Time: $(($SECONDS-$TSEC))"
#     cid="console-setup"
#     __get_admin_token
#     TOKEN=$(get_client_token nonrtric-realm $cid)
#     decode_token "$TOKEN"
#     curl -v localhost:31789/ -H "Authorization: Bearer $TOKEN"
#     sleep 5
# done
cid="console-setup"
__get_admin_token
TOKEN=$(get_client_token nonrtric-realm $cid)
decode_token "$TOKEN"

##################################################################################
echo "##### Installing charts: strimzi and nrt-base-1"
##################################################################################

helm repo add strimzi https://strimzi.io/charts/

helm install --wait strimzi-kafka-crds -n nonrtric strimzi/strimzi-kafka-operator


cp opa-rules/bundle.tar.gz helm/nrt-base-1/charts/opa-rule-db/data

#envsubst < helm/nrt-base-1/charts/httpecho/values-template.yaml > helm/nrt-base-1/charts/httpecho/values.yaml

helm install -n nonrtric nrt-base-1 helm/nrt-base-1


retcode=1
while [ $retcode -eq 1 ]; do
    retcode=0
    CONFIG=$(kubectl exec -n nonrtric influxdb2-0 -- influx config ls --json)
    if [ $? -ne 0 ]; then
        retcode=1
        sleep 1
    elif [ "$CONFIG" == "{}" ]; then
        echo "Configuring db"
        kubectl exec -n nonrtric influxdb2-0 -- influx setup -u bm -p mySuP3rS3cr3tT0keN -o est -b pm-bucket -f
        if [ $? -ne 0 ]; then
            retcode=1
            sleep 1
        fi
    else
        echo "Db user configured, skipping"
    fi
done

# Save influx user api-token to secret
INFLUXDB2_TOKEN=$(get_influxdb2_token influxdb2-0 nonrtric)
INFLUXDB2_TOKEN=$(echo -n $INFLUXDB2_TOKEN | base64)
PATCHDATA='[{"op": "add", "path": "/data/token", "value": "'$INFLUXDB2_TOKEN'" }]'
kubectl patch secret influxdb-api-token -n nonrtric --type json -p "$PATCHDATA"


echo "Wait for kafka"
_ts=$SECONDS
until $(kubectl exec -n nonrtric kafka-client -- kafka-topics --list --bootstrap-server kafka-1-kafka-bootstrap.nonrtric:9092 1> /dev/null 2> /dev/null); do
    echo -ne "  $(($SECONDS-$_ts)) sec, retrying at $(($SECONDS-$_ts+5)) sec                        $SAMELINE"
    sleep 5
done
echo ""

# Pre-create known topic to avoid losing data when autocreated by apps
__topics_list="file-ready collected-file json-file-ready-kp json-file-ready-kpadp pmreports"
for __topic in $__topics_list; do
    create_topic kafka-1-kafka-bootstrap.nonrtric:9092 $__topic 10
done

echo ""

##################################################################################
echo "##### Installing: chart ran"
##################################################################################

./helm/ran/certs/gen-certs.sh 10
check_error $?

helm install --wait -n ran ran helm/ran

echo ""

##################################################################################
echo "##### Installing chart: nrt-pm"
##################################################################################


cwd=$PWD
echo "Updating dfc truststore"
cd helm/nrt-pm/charts/dfc/truststore
cp template-truststore.jks truststore.jks
check_error $?

echo " Adding https ca cert to dfc truststore"
cat <<__EOF__ | keytool -importcert -alias pm-https -file $cwd/helm/ran/certs/httpsca.crt -keystore truststore.jks -storetype JKS -storepass $(< truststore.pass)
yes
__EOF__
cd $cwd

cid="kafka-producer-pm-xml2json"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export APP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm/charts/kafka-producer-pm-xml2json/values-template.yaml > helm/nrt-pm/charts/kafka-producer-pm-xml2json/values.yaml


cid="kafka-producer-pm-json2kafka"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export APP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm/charts/kafka-producer-pm-json2kafka/values-template.yaml > helm/nrt-pm/charts/kafka-producer-pm-json2kafka/values.yaml


cid="kafka-producer-pm-json2influx"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export APP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm/charts/kafka-producer-pm-json2influx/values-template.yaml > helm/nrt-pm/charts/kafka-producer-pm-json2influx/values.yaml


cid="pm-producer-json2kafka"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export APP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm/charts/pm-producer-json2kafka/values-template.yaml > helm/nrt-pm/charts/pm-producer-json2kafka/values.yaml


cid="dfc"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export APP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm/charts/dfc/values-template.yaml > helm/nrt-pm/charts/dfc/values.yaml


#envsubst < helm/nrt-pm/charts/ics/values-template.yaml > helm/nrt-pm/charts/ics/values.yaml

helm install --wait -n nonrtric nrt-pm helm/nrt-pm

echo ""

echo "######################################################################"
echo "ranpm installed"
echo "Wait until all pods are running before installation additional charts"
echo "Do: 'kubectl get po -n nonrtric' and verify that all pods are in status Running"
echo " and all included containers are Ready"
echo "######################################################################"
