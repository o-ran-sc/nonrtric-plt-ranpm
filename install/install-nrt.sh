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
export KUBERNETESHOST=$(kube_get_controlplane_host)
if [ $? -ne 0 ]; then
    echo $KUBERNETESHOST
    echo "Exiting"
    exit 1
fi

echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
echo "Kubernetes control plane host: $KUBERNETESHOST"
echo "Host obtained from current kubectl context"
echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"

echo "+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++"
echo "Checking requirements"
echo " Checking if istio is installed"
kubectl $KUBECONF get authorizationpolicies -A &> /dev/null
if [ $? -ne 0 ]; then
    echo "  Istio api: kubectl get authorizationpolicies is not installed"
    exit 1
else
    echo "  OK"
fi
echo " Checking if jq is installed"
tmp=$(type jq)
if [ $? -ne 0 ]; then
	echo "  Command utility jq (cmd-line json processor) is not installed"
	exit 1
else
    echo "  OK"
fi
echo " Checking if envsubst is installed"
tmp=$(type envsubst)
if [ $? -ne 0 ]; then
	echo "  Command utility envsubst (env var substitution in files) is not installed"
	exit 1
else
    echo "  OK"
fi

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
echo "##### Installing chart: nrt-base-0"
##################################################################################
helm install --wait --create-namespace -n nonrtric nrt-base-0 helm/nrt-base-0

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

##################################################################################
echo "##### Installing charts: strimzi and nrt-base-1"
##################################################################################

helm repo add strimzi https://strimzi.io/charts/

helm install --wait strimzi-kafka-crds -n nonrtric strimzi/strimzi-kafka-operator


cp opa-rules/bundle.tar.gz helm/nrt-base-1/charts/opa-rule-db/data

helm install -n nonrtric nrt-base-1 helm/nrt-base-1

echo "Waiting for influx db - there may be error messages while trying..."
retcode=1
while [ $retcode -eq 1 ]; do
    retcode=0
    CONFIG=$(kubectl exec -n nonrtric influxdb2-0 -- influx config ls --json)
    if [ $? -ne 0 ]; then
        retcode=1
        sleep 1
    elif [ "$CONFIG" == "{}" ]; then
        echo "Configuring db"
        kubectl exec -n nonrtric influxdb2-0 -- influx setup -u admin -p mySuP3rS3cr3tT0keN -o est -b pm-bucket -f
        if [ $? -ne 0 ]; then
            retcode=1
            sleep 1
        fi
    else
        echo "Db user configured, skipping"
    fi
done

# Save influx user api-token to secret
B64FLAG="-w 0"
case "$OSTYPE" in
  darwin*)  B64FLAG="" ;;
esac
INFLUXDB2_TOKEN=$(get_influxdb2_token influxdb2-0 nonrtric | base64 $B64FLAG)
PATCHDATA='[{"op": "add", "path": "/data/token", "value": "'$INFLUXDB2_TOKEN'"}]'
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

helm install --wait --create-namespace -n ran -f helm/global-values.yaml ran helm/ran

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

helm install --wait -f helm/global-values.yaml -n nonrtric nrt-pm helm/nrt-pm

echo ""

echo "######################################################################"
echo "ranpm installed"
echo "Wait until all pods are running before installation additional charts"
echo "Do: 'kubectl get po -n nonrtric' and verify that all pods are in status Running"
echo " and all included containers are Ready"
echo "######################################################################"