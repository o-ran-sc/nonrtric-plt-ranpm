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

HTTPS_PORT=443

print_usage() {
    echo "Usage: push-to-ves-collector.sh <node-count> <num-of-events> <node-name-base> <file-extension> <num-servers>"
    exit 1
}
echo $@
if [ $# -lt 5 ]; then
    print_usage
fi

rm .out.json
touch .out.json

BEGINTIME=$(date +%s -d '1 hour ago')
TIMEZONE="+0100"
CURTIME=$BEGINTIME
COLLECTIONTIME=$(date +%a,%d%m%Y%H:%M:%S%Z)

for (( i=0; i<$EVT_COUNT; i++)); do

    echo "EVENT BATCH: $i of $EVT_COUNT events for $NODE_COUNT NODES - 1 FILE PER EVENT"
    echo '{"eventList": [' > .out.json
    first=0

    let STTIMEMS=$CURTIME*1000000
    ST=$(date -d @$CURTIME +'%Y%m%d.%H%M')
    let CURTIME=CURTIME+900
    let CURTIMEMS=$CURTIME*1000000
    ET=$(date -d @$CURTIME +'%H%M')

    for (( j=0; j<$NODE_COUNT; j++)); do

            NO="$NODE_NAME_BASE-$j"
            #FN="A20000626.2315+0200-2330+0200_$NO-$i.$FILE_EXT"
            FN="A$ST+0200-$ET+0200_$NO-$i.$FILE_EXT"
            let SRV_ID=$j%$SRV_COUNT
            let SRV_ID=SRV_ID+1
            echo "FILENAME "$FN
            SRV="pm-https-server-$SRV_ID"
            echo "HTTP SERVER "$SRV
            URL="https://$SRV:$HTTPS_PORT/generatedfiles/$FN"
            EVT='{"commonEventHeader":{"startEpochMicrosec":'$STTIMEMS',"eventId":"FileReady_1797490e-10ae-4d48-9ea7-3d7d790b25e1","timeZoneOffset":"UTC'$TIMEZONE'","internalHeaderFields":{"collectorTimeStamp":"'$COLLECTIONTIME'"},"priority":"Normal","version":"4.0.1","reportingEntityName":"'$NO'","sequence":0,"domain":"notification","lastEpochMicrosec":'$CURTIMEMS',"eventName":"Notification_gnb-Ericsson_FileReady","vesEventListenerVersion":"7.0.1","sourceName":"'$NO'"},"notificationFields":{"notificationFieldsVersion":"2.0","changeType":"FileReady","changeIdentifier":"PM_MEAS_FILES","arrayOfNamedHashMap":[{"name":"'$FN'","hashMap":{"location":"'$URL'","fileFormatType":"org.3GPP.32.435#measCollec","fileFormatVersion":"V10","compression":"gzip"}}]}}'
            if [ $first -ne 0 ]; then
                echo "," >> .out.json
            fi
            first=1
            echo "$EVT" >> .out.json
    done
    echo ']}' >> .out.json
    RES=$(curl -s -X POST 'localhost:8080/eventListener/v7/eventBatch' --header 'Content-Type: application/json' --data-binary @.out.json)
    echo $RES
done

