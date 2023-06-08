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

# Generic error printout function
# args: <numeric-response-code> <descriptive-string>
check_error() {
    if [ $1 -ne 0 ]; then
        echo "Failed: $2"
        echo "Exiting..."
        exit 1
    fi
}

export KUBERNETESHOST=$(kube_get_controlplane_host)
if [ $? -ne 0 ]; then
    echo $KUBERNETESHOST
    echo "Exiting"
    exit 1
fi

echo "Kubernetes control plane host: $KUBERNETESHOST"

. scripts/kube_get_nodeport.sh
. scripts/get_influxdb2_token.sh
. scripts/create_influxdb2_bucket.sh
. scripts/update_ics_job.sh

echo "Installation of pm to influx job"

echo " Retriving influxdb2 access token..."
INFLUXDB2_TOKEN=$(get_influxdb2_token influxdb2-0 nonrtric)


bucket=pm-logg-bucket
echo "Creating bucket $bucket in influxdb2"
create_influxdb2_bucket influxdb2-0 nonrtric $bucket

. scripts/populate_keycloak.sh

cid="console-setup"
TOKEN=$(get_client_token nonrtric-realm $cid)

JOB='{
       "info_type_id": "PmData",
       "job_owner": "console",
       "job_definition": {
          "filter": {
             "sourceNames": ["node2-1"],
             "measObjInstIds": [],
             "measTypeSpecs": [
                {
                   "measuredObjClass": "NRCellDU",
                   "measTypes": [
                      "pmCounterNumber102"
                   ]
                }
             ],
             "measuredEntityDns": []
          },
          "deliveryInfo": {
             "topic": "pmreports",
             "bootStrapServers": "kafka-1-kafka-bootstrap.nonrtric:9097"
          }
       }
    }'
echo $JOB > .job.json
update_ics_job pmlog $TOKEN

echo "done"

