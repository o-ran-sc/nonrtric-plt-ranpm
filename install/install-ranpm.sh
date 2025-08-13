#!/bin/bash

#  ============LICENSE_START===============================================
#  Copyright (C) 2023 Nordix Foundation. All rights reserved.
#  Copyright (C) 2023-2025 OpenInfra Foundation Europe. All rights reserved.
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

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
cd "${ROOT_DIR}"

# Deprecated: This script is deprecated and will be removed in future releases.
echo "******************** DEPRECATION NOTICE ************************"
echo "This installation script is no longer maintained or supported."
echo "Please follow the latest installation https://github.com/o-ran-sc/it-dep/tree/master/smo-install#readme for the recommended and actively maintained setup."
echo -e "A dedicated flavour configuration for installing RANPM is available here (https://github.com/o-ran-sc/it-dep/tree/master/smo-install/helm-override/ranpm.)"
echo -e "*****************************************************************\n"

# Array of installation scripts
scripts=("install-nrt.sh" "install-pm-log.sh" "install-pm-influx-job.sh" "install-pm-rapp.sh")

for script in "${scripts[@]}"; do
  echo "*****************************************************************"
  echo "Running ${script}"
  echo "*****************************************************************"
  chmod +x "${ROOT_DIR}/${script}"
  "${ROOT_DIR}/${script}"
  if [ $? -eq 0 ]; then
    echo "*****************************************************************"
    echo "${script} completed"
    echo "*****************************************************************"
  else
    exit 1
  fi
done

echo "*****************************************************************"
echo "*****************************************************************"
echo "All RANPM installation scripts executed successfully!"
echo "*****************************************************************"
echo "*****************************************************************"