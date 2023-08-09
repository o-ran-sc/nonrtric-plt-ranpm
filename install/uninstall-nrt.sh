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

# Error printout and logging
check_error() {
    if [ $1 -eq 0 ]; then
        echo " Uninstall $2 OK"
    else
        echo " Unistall $2 failed"
        let NUM_ERRORS=NUM_ERRORS+1
    fi
}

NUM_ERRORS=0

helm uninstall -n ran ran

helm uninstall -n nonrtric nrt-pm

helm uninstall -n nonrtric nrt-base-1

helm uninstall -n nonrtric nrt-base-0

INST="strimzi-kafka CRDs"
echo "##########################"
echo "Uninstall $INST"

helm repo remove strimzi
helm uninstall   -n nonrtric strimzi-kafka-crds
check_error $? "$INST"

# Print final result
if [ $NUM_ERRORS -eq 0 ]; then
    echo "Uninstall PM Demo OK"
else
    echo "Uninstall PM Demo failed, $NUM_ERRORS failures"
    exit 1
fi

exit 0
