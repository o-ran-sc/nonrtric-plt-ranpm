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
. scripts/create_influxdb2_bucket.sh

echo "Installing: pm connect"
echo " Retriving influxdb2 access token..."
export INFLUXDB2_TOKEN=$(get_influxdb2_token influxdb2-0 nonrtric)

echo "influxdb2_connection_bean=#class:com.influxdb.client.InfluxDBClientFactory#create('http://influxdb2.nonrtric:8086', '$INFLUXDB2_TOKEN')" > .tmp.decode.input
export PM_KAFKA_CONNECT_BEAN=$(base64 -i .tmp.decode.input)

envsubst '${PM_KAFKA_CONNECT_BEAN}' < helm/nrt-pm-kafka-connect/values-template.yaml > helm/nrt-pm-kafka-connect/values.yaml

bucket=ts_pms_metrics
echo "Creating bucket $bucket in influxdb2"
create_influxdb2_bucket influxdb2-0 nonrtric $bucket

echo " helm install..."
helm install -n nonrtric nrt-pm-kafka-connect helm/nrt-pm-kafka-connect

echo "done"

