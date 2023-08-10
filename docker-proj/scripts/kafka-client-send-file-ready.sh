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
echo "script-home: "$SD
cd $SD
CWD=$PWD

NODE_COUNT=$1
EVT_COUNT=$2
NODE_NAME_BASE=$3
FILE_EXT=$4
SRV_COUNT=$5

print_usage() {
    echo "Usage: kafka-client-send-file-ready.sh <node-count> <num-of-events> <node-name-base> <file-extension> <num-servers>"
    exit 1
}
echo $@
if [ $# -lt 5 ]; then
    print_usage
fi

BEGINTIME=1665146700
CURTIME=$BEGINTIME

BATCHSIZE=1000

CNTR=0
TCNTR=0

for (( i=0; i<$EVT_COUNT; i++)); do

    if [ $CNTR -eq 0 ]; then
        rm .out.json
        touch .out.json
    fi

    echo "EVENT NO: $i for $NODE_COUNT NODES - 1 FILE PER EVENT"

    ST=$(date -d @$CURTIME +'%Y%m%d.%H%M')
    let CURTIME=CURTIME+900
    ET=$(date -d @$CURTIME +'%H%M')

    for (( j=0; j<$NODE_COUNT; j++)); do

            NO="$NODE_NAME_BASE-$j"

            #FN="A20000626.2315+0200-2330+0200_$NO-$i.$FILE_EXT"
            FN="A$ST+0200-$ET+0200_$NO-$i.$FILE_EXT"
            let SRV_ID=$j%$SRV_COUNT
            let SRV_ID=SRV_ID+1
            echo "NODE "$NO
            echo "FILENAME "$FN
            SRV="pm-https-server-$SRV_ID"
            echo "HTTP SERVER "$SRV
            URL="https://$SRV:$HTTPS_PORT/files/$FN"
            EVT='{"event":{"commonEventHeader":{"sequence":0,"eventName":"Noti_RnNode-Ericsson_FileReady","sourceName":"'$NO'","lastEpochMicrosec":151983,"startEpochMicrosec":15198378,"timeZoneOffset":"UTC+05:00","changeIdentifier":"PM_MEAS_FILES"},"notificationFields":{"notificationFieldsVersion":"notificationFieldsVersion","changeType":"FileReady","changeIdentifier":"PM_MEAS_FILES","arrayOfNamedHashMap":[{"name":"'$FN'","hashMap":{"fileFormatType":"org.3GPP.32.435#measCollec","location":"'$URL'","fileFormatVersion":"V10","compression":"gzip"}}]}}}'
            echo $EVT >> .out.json

        let CNTR=CNTR+1
        let TCNTR=TCNTR+1
        if [ $CNTR -ge $BATCHSIZE ]; then
            echo "Pushing batch of $CNTR events"
            cat .out.json | /opt/kafka/bin/kafka-console-producer.sh --topic file-ready --broker-list kafka-1:9092
            rm .out.json
            touch .out.json
            CNTR=0
        fi
    done

done
if [ $CNTR -ne 0 ]; then
    echo "Pushing batch of $CNTR events"
    cat .out.json | /opt/kafka/bin/kafka-console-producer.sh --topic file-ready --broker-list kafka-1:9092
fi

echo "Pushed $TCNTR events"
