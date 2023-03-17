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

# args: <service-name> <ns> <port-name>
kube_get_nodeport() {
	if [ $# -ne 3 ]; then
    	echo"kube_get_nodeport need 3 args, <service-name> <ns> <port-name>" $@
		exit 1
	fi

	for timeout in {1..60}; do
		port=$(kubectl get svc $1  -n $2 -o jsonpath='{...ports[?(@.name=="'$3'")].nodePort}')
		if [ $? -eq 0 ]; then
			if [ ! -z "$port" ]; then
				echo $port
				return 0
			fi
		fi
		sleep 0.5
	done
	echo "0"
	return 1
}