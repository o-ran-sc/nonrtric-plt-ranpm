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
. scripts/get_influxdb2_token.sh
. scripts/create_influxdb2_bucket.sh

echo "Installing pmlog"

# Variables
export KUBERNETESHOST=$(kube_get_controlplane_host)
if [ $? -ne 0 ]; then
    echo $KUBERNETESHOST
    echo "Exiting"
    exit 1
fi

# Generic error printout function
# args: <numeric-response-code> <descriptive-string>
check_error() {
    if [ $1 -ne 0 ]; then
        echo "Failed: $2"
        echo "Exiting..."
        exit 1
    fi
}

. scripts/populate_keycloak.sh

cid="nrt-pm-log"
create_clients nonrtric-realm $cid
check_error $?
generate_client_secrets nonrtric-realm $cid
check_error $?

export APP_CLIENT_SECRET=$(< .sec_nonrtric-realm_$cid)

envsubst < helm/nrt-pm-log/values-template.yaml > helm/nrt-pm-log/values.yaml

bucket=pm-logg-bucket
echo "Creating bucket $bucket in influxdb2"
create_influxdb2_bucket influxdb2-0 nonrtric $bucket

echo " helm install..."
helm install -n nonrtric nrt-pm-log helm/nrt-pm-log

echo "done"
