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

echo "Generating https certs"
SD=$(dirname -- "$0")
echo "script-home: "$SD

cd $SD

print_usage() {
    echo "Usage: gen-certs.sh <num-certs>"
    exit 1
}

check_error() {
    if [ $1 -ne 0 ]; then
        echo "Failed $2"
        echo "Exiting..."
        exit 1
    fi
}

if [ $# -ne 1 ]; then
    print_usage
fi

rm *.crt
rm *.key

echo "Generating ca cert and key"
echo " Generating ca key"
openssl genrsa 2048 > ca.key  2> /dev/null
check_error $?

echo " Generating ca cert"
openssl req -new -x509 -nodes -days 365000  -key ca.key -subj "/C=SE/ST=./L=./O=EST/OU=EST/CN=$SRV/emailAddress=a@example.com" -out httpsca.crt 2> /dev/null

check_error $?


for (( i=0; i<${1}; i++ )); do
    SRV="pm-https-server-$i.pm-https-server.ran"

    echo " Generating cert and key  for server $SRV"
    openssl req -newkey rsa:2048 -nodes -days 365000 -subj "/C=SE/ST=./L=./O=ERIC/OU=ERIC/CN=$SRV/emailAddress=a@example.com" -keyout https-$i.key -out https-req$i.crt 2> /dev/null

    check_error $?

    openssl x509 -req -days 365000 -set_serial 01 -in https-req$i.crt -out https-$i.crt -CA httpsca.crt -CAkey ca.key
    check_error $?
    echo " Verifying cert towards ca cert"
    openssl verify -CAfile httpsca.crt httpsca.crt https-$i.crt
    check_error $?

done

echo "DONE"
exit 0

