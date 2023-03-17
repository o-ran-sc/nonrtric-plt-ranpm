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


# Generic function for waiting for 200/201 http response
# args: <url> <server-name>
wait_for_server_ok() {
    echo "Waiting for server ok from: $1 - $2"
    _ts=$SECONDS
    retcode=1
    while [ $retcode -ne 0 ]; do
        STAT=$(curl -m 60 -s -w '%{http_code}' $1)
        retcode=$?
        _msg="time: $(($SECONDS-$_ts)) sec"
        if [ $retcode -eq 0 ]; then
            status=${STAT:${#STAT}-3}
            if [ "$status" == "200" ] || [ "$status" == "201" ]; then
                _msg=$_msg", http status $status, OK"
            else
                _msg=$_msg", http status $status, retrying"
                retcode=1
            fi
        else
            _msg=$_msg", curl return $retcode, retrying"
        fi
        echo -ne "  $_msg                        $SAMELINE"
        if [ $retcode -eq 0 ]; then
            echo ""
            return 0
        fi
        sleep 1
    done
}