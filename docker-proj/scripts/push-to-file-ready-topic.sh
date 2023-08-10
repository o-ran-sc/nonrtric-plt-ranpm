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
SD=$(dirname -- "$0")
echo "${0##*/} script-home: "$SD
cd $SD
CWD=$PWD


NODE_COUNT=$1
EVT_COUNT=$2
NODE_NAME_BASE=$3
FILE_EXT=$4
SRV_COUNT=$5

print_usage() {
    echo "Usage: push-to-file-ready-topic.sh <node-count> <num-of-events> <node-name-base> <file-extension> <num-servers>"
    exit 1
}
if [ $# -lt 5 ]; then
    print_usage
fi

chmod +x kafka-client-send-file-ready.sh
docker cp kafka-client-send-file-ready.sh common-kafka-1-1:/tmp/

docker exec -it common-kafka-1-1 /tmp/kafka-client-send-file-ready.sh $NODE_COUNT $EVT_COUNT $NODE_NAME_BASE $FILE_EXT $SRV_COUNT

echo done

