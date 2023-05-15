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



echo "Installing pmrapp"

. scripts/kube_get_controlplane_host.sh
. scripts/kube_get_nodeport.sh
. scripts/create_topic.sh

# Generic error printout function
# args: <numeric-response-code> <descriptive-string>
check_error() {
    if [ $1 -ne 0 ]; then
        echo "Failed: $2"
        echo "Exiting..."
        exit 1
    fi
}

echo "Creating client in keycloak"

# Find host and port to keycloak
export KUBERNETESHOST=$(kube_get_controlplane_host)
if [ $? -ne 0 ]; then
    echo $KUBERNETESHOST
    echo "Exiting"
    exit 1
fi

create_topic kafka-1-kafka-bootstrap.nonrtric:9092 rapp-topic 10

. scripts/populate_keycloak.sh

cid="pm-rapp"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export PMRAPP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm-rapp/values-template.yaml > helm/nrt-pm-rapp/values.yaml

echo " helm install..."
helm install --wait -f helm/global-values.yaml -n nonrtric nrt-pm-rapp helm/nrt-pm-rapp

echo "done"

