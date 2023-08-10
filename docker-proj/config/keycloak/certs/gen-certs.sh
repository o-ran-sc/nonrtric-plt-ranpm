#!/bin/sh

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

BASEDIR=$(dirname $(realpath "$0"))
PASSWORD=changeit
CANAME=rootCA
DAYS=3650
DOMAIN_NAME="CN=keycloak"
CA_SUBJECT="/CN=keycloak"

rm $BASEDIR/keycloak.server.keystore.p12 $BASEDIR/keycloak.client.truststore.p12 $BASEDIR/rootCA.crt $BASEDIR/rootCA.key $BASEDIR/rootCA.srl $BASEDIR/cert-signed $BASEDIR/cert-file 2>/dev/null

echo $PASSWORD > $BASEDIR/secretfile.txt

echo "Generating Root Certificate"
openssl req -x509 -sha256 -days $DAYS -newkey rsa:4096 -keyout $BASEDIR/${CANAME}.key -subj "$CA_SUBJECT" -passout file:$BASEDIR/secretfile.txt -out $BASEDIR/${CANAME}.crt
echo "Create server certificate for Keycloak"
keytool -keystore $BASEDIR/keycloak.server.keystore.p12 -storetype pkcs12 -keyalg RSA -alias keycloak -validity $DAYS -genkey -storepass $PASSWORD -keypass $PASSWORD -dname $DOMAIN_NAME -ext SAN=DNS:keycloak
echo "Create keycloak keystore with server certificate"
keytool -keystore $BASEDIR/keycloak.server.keystore.p12 -storetype pkcs12 -alias keycloak -storepass $PASSWORD -keypass $PASSWORD -certreq -file $BASEDIR/cert-file
echo "Sign server certificate with rootCA"
openssl x509 -req -CA $BASEDIR/${CANAME}.crt -CAkey $BASEDIR/${CANAME}.key -in $BASEDIR/cert-file -out $BASEDIR/cert-signed -days $DAYS -CAcreateserial -passin pass:$PASSWORD
echo "Add $CANAME to keystore"
keytool -keystore $BASEDIR/keycloak.server.keystore.p12 -alias CARoot -storepass $PASSWORD -keypass $PASSWORD -import -file $BASEDIR/${CANAME}.crt -noprompt
echo "Add signed server certificate to keystore"
keytool -keystore $BASEDIR/keycloak.server.keystore.p12 -alias keycloak -storepass $PASSWORD -keypass $PASSWORD -import -file $BASEDIR/cert-signed -noprompt
echo "Create keycloak truststore with $CANAME"
keytool -keystore $BASEDIR/keycloak.client.truststore.p12 -storetype pkcs12 -alias ca -storepass $PASSWORD -keypass $PASSWORD -import -file $BASEDIR/${CANAME}.crt -noprompt
rm $BASEDIR/secretfile.txt 2>/dev/null
