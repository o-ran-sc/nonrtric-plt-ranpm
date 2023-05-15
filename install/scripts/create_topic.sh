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

# Script to create a topic in kafka

# Create a topic
# args:  <kafka-bootstrap-pod.namespace:<port> <topic-name> [<num-partitions>]
create_topic() {

    if [ $# -lt 2 ] && [ $# -gt 3 ]; then
        echo "Usage: create-topic.sh <kafka-bootstrap-svc.namespace> <topic-name> [<num-partitions>]"
        exit 1
    fi
    kafka=$1
    topic=$2
    partitions=$3

    if [ -z "$partitions" ]; then
        partitions=1
    fi

    echo "Creating topic: $topic with $partitions partition(s) in $kafka"

    kubectl exec -it kafka-client -n nonrtric -- bash -c 'kafka-topics --create --topic '$topic'  --partitions '$partitions' --bootstrap-server '$kafka

    return $?
}
