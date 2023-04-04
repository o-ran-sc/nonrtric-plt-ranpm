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
TYPE=$5
SRV_COUNT=$6
HIST=$7

FTPES_PORT=2021
SFTP_PORT=2022
HTTPS_PORT=443

print_usage() {
    echo "Usage: kafka-client-send-genfiles-file-ready.sh <node-count> <num-of-events> <node-name-base> <file-extension> sftp|ftpes|https <num-servers> [hist]"
    exit 1
}
echo $@
if [ $# -lt 6 ] && [ $# -gt 7 ]; then
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

HIST_LEN=0
if [ ! -z "$HIST" ]; then
    if [ $HIST != "hist" ]; then
        print_usage
    fi
    HIST_LEN=96
fi

# Unix time of 20230220.1300
# If the value is changed, make sure to set the same time to the env var GENERATED_FILE_START_TIME in kube-plt.yaml for the https-server
BEGINTIME=1676898000
# Time zone
# If the value is changed, make sure to set the same value to the env var GENERATED_FILE_TIMEZONE in kube-plt.yaml for the https-server
TIMEZONE="+0100"
CURTIME=$BEGINTIME

BATCHSIZE=1000

CNTR=0
TCNTR=0

for (( i=0; i<$EVT_COUNT; i++)); do

    if [ $CNTR -eq 0 ]; then
        rm .out.json
        touch .out.json
    fi

    if [ "$HIST" == "" ]; then
        echo "EVENT NO: $i for $NODE_COUNT NODES - 1 FILE PER EVENT"
    else
        echo "EVENT NO: $i for $NODE_COUNT NODES - $HIST_LEN FILES PER EVENT"
    fi
    let STTIMEMS=$CURTIME*1000000
    ST=$(date -d @$CURTIME +'%Y%m%d.%H%M')
    let CURTIME=CURTIME+900
    let CURTIMEMS=$CURTIME*1000000
    ET=$(date -d @$CURTIME +'%H%M')

    for (( j=0; j<$NODE_COUNT; j++)); do

        if [ "$HIST" == "" ]; then
            NO="$NODE_NAME_BASE-$j"

            #FN="A20000626.2315+0200-2330+0200_$NO-$i.$FILE_EXT"
            FN="A$ST$TIMEZONE-$ET${TIMEZONE}_$NO.$FILE_EXT"
            let SRV_ID=$j%$SRV_COUNT
            #let SRV_ID=SRV_ID+1
            echo "NODE "$NO
            echo "FILENAME "$FN


            if [ $TYPE == "sftp" ]; then
                SRV="ftp-sftp-$SRV_ID"
                echo "FTP SERVER "$SRV
                URL="sftp://onap:pano@$SRV:$SFTP_PORT/$FN"
            elif [ $TYPE == "ftpes" ]; then
                SRV="ftp-ftpes-$SRV_ID"
                echo "FTP SERVER "$SRV
                URL="ftpes://onap:pano@$SRV:$FTPES_PORT/$FN"
            elif [ $TYPE == "https" ]; then
                SRV="pm-https-server-$SRV_ID.pm-https-server.ran"
                echo "HTTP SERVER "$SRV
                URL="https://$SRV:$HTTPS_PORT/generatedfiles/$FN"
            fi
            EVT='{"event":{"commonEventHeader":{"sequence":0,"eventName":"Noti_RnNode-Ericsson_FileReady","sourceName":"'$NO'","lastEpochMicrosec":'$CURTIMEMS',"startEpochMicrosec":'$STTIMEMS',"timeZoneOffset":"UTC'$TIMEZONE'","changeIdentifier":"PM_MEAS_FILES"},"notificationFields":{"notificationFieldsVersion":"notificationFieldsVersion","changeType":"FileReady","changeIdentifier":"PM_MEAS_FILES","arrayOfNamedHashMap":[{"name":"'$FN'","hashMap":{"fileFormatType":"org.3GPP.32.435#measCollec","location":"'$URL'","fileFormatVersion":"V10","compression":"gzip"}}]}}}'
            echo $EVT >> .out.json
        else
            NO="$NODE_NAME_BASE-$j"

            let SRV_ID=$j%$SRV_COUNT
            #let SRV_ID=SRV_ID+1
            echo "NODE "$NO

            EVT_FRAG=""
            for(( k=95; k>=0; k-- )); do

                let FID=$i-k
                CURTIME=$(($BEGINTIME+$FID*900))
                let STTIMEMS=$CURTIME*1000000
                ST=$(date -d @$CURTIME +'%Y%m%d.%H%M')
                let CURTIME=CURTIME+900
                let CURTIMEMS=$CURTIME*1000000
                ET=$(date -d @$CURTIME +'%H%M')
                if [ $FID -lt 0 ]; then
                    FN="NONEXISTING_$NO.$FILE_EXT"
                else
                    #FN="A20000626.2315+0200-2330+0200_$NO-$FID.$FILE_EXT"
                    FN="A$ST$TIMEZONE-$ET${TIMEZONE}_$NO.$FILE_EXT"
                fi
                echo "FILENAME "$FN
                if [ $TYPE == "sftp" ]; then
                    SRV="ftp-sftp-$SRV_ID"
                    #echo "FTP SERVER "$SRV
                    URL="sftp://onap:pano@$SRV:$SFTP_PORT/$FN"
                elif [ $TYPE == "ftpes" ]; then
                    SRV="ftp-ftpes-$SRV_ID"
                    #echo "FTP SERVER "$SRV
                    URL="ftpes://onap:pano@$SRV:$FTPES_PORT/$FN"
                elif [ $TYPE == "https" ]; then
                    SRV="pm-https-server-$SRV_ID.pm-https-server.ran"
                    #echo "HTTP SERVER "$SRV
                    URL="https://$SRV:$HTTPS_PORT/files/$FN"
                fi
                if [ "$EVT_FRAG" != "" ]; then
                    EVT_FRAG=$EVT_FRAG","
                fi
                EVT_FRAG=$EVT_FRAG'{"name":"'$FN'","hashMap":{"fileFormatType":"org.3GPP.32.435#measCollec","location":"'$URL'","fileFormatVersion":"V10","compression":"gzip"}}'
            done

            EVT='{"event":{"commonEventHeader":{"sequence":0,"eventName":"Noti_RnNode-Ericsson_FileReady","sourceName":"'$NO'","lastEpochMicrosec":'$CURTIMEMS',"startEpochMicrosec":'$STTIMEMS',"timeZoneOffset":"UTC'$TIMEZONE'","changeIdentifier":"PM_MEAS_FILES"},"notificationFields":{"notificationFieldsVersion":"notificationFieldsVersion","changeType":"FileReady","changeIdentifier":"PM_MEAS_FILES","arrayOfNamedHashMap":['$EVT_FRAG']}}}'
            echo $EVT >> .out.json

        fi

        let CNTR=CNTR+1
        let TCNTR=TCNTR+1
        if [ $CNTR -ge $BATCHSIZE ]; then
            echo "Pushing batch of $CNTR events"
            cat .out.json | kafka-console-producer --topic file-ready --broker-list kafka-1-kafka-bootstrap.nonrtric:9092
            rm .out.json
            touch .out.json
            CNTR=0
        fi
    done
done
if [ $CNTR -ne 0 ]; then
    echo "Pushing batch of $CNTR events"
    cat .out.json | kafka-console-producer --topic file-ready --broker-list kafka-1-kafka-bootstrap.nonrtric:9092
fi

echo "Pushed $TCNTR events"
