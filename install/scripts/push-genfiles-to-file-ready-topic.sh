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
TYPE=$5
SRV_COUNT=$6
HIST=$7

print_usage() {
    echo "Usage: push-genfiles-to-file-ready-topic.sh <node-count> <num-of-events> <node-name-base> <file-extension> sftp|ftpes|https <num-servers> [hist]"
    exit 1
}
if [ $# -lt 6 ] || [ $# -gt 7 ]; then
    print_usage
fi

if [ $TYPE == "sftp" ]; then
    echo "sftp servers not yet supported"
elif [ $TYPE == "ftpes" ]; then
    echo "ftpes servers not yet supported"
elif [ $TYPE == "https" ]; then
    :
else
    print_usage
fi

if [ $FILE_EXT != "xml.gz" ]; then
    echo "only xml.gz format supported"
    print_usage
fi

if [ ! -z "$HIST" ]; then
    if [ $HIST != "hist" ]; then
        print_usage
    fi
fi

if [ "$KUBECONFIG" == "" ]; then
    echo "Env var KUBECONFIG not set, using current settings for kubectl"
else
    echo "Env var KUBECONFIG set to $KUBECONFIG"
fi

chmod +x kafka-client-send-genfiles-file-ready.sh
kubectl cp kafka-client-send-genfiles-file-ready.sh nonrtric/kafka-client:/home/appuser

kubectl exec kafka-client -n nonrtric -- bash -c './kafka-client-send-genfiles-file-ready.sh file-ready '$NODE_COUNT' '$EVT_COUNT' '$NODE_NAME_BASE' '$FILE_EXT' '$TYPE' '$SRV_COUNT' '$HIST

echo done

