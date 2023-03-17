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

# Script to create a bucket in influxdb2

# args: <influxdb2-instance> <namespace> <bucket-name>
create_influxdb2_bucket() {
    RES=$(kubectl exec -n $2 $1 -- influx bucket ls --json | jq 'any(.[].name; . == "'$3'")')
    if [ "$RES" == "true" ]; then
        echo "Bucket $BCK exist, OK"
    elif [ "$RES" == "false" ]; then
        echo "Bucket $BCK does not exist, creating"
        RES=$(kubectl exec -n $2 $1 -- influx bucket create -n $3)
        if [ $? -eq 0 ]; then
            echo "Bucket $3 created, OK"
            return 0
        else
            echo "Cannot create bucket $3, exiting"
            return 1
        fi
    else
        echo "Cannot check if bucket $3 exists, exiting"
        return 1
    fi
}