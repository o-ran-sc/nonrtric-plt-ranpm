#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
cd "${ROOT_DIR}"

# Array of scripts to execute
scripts=("uninstall-pm-rapp.sh" "uninstall-nrt.sh")

for script in "${scripts[@]}"; do
  echo "*****************************************************************"
  echo "Running ${script}"
  echo "*****************************************************************"
  chmod +x "${ROOT_DIR}/${script}"
  /bin/bash "${ROOT_DIR}/${script}"
  echo "*****************************************************************"
  echo "${script} completed"
  echo "*****************************************************************"
done

echo "*****************************************************************"
echo "*****************************************************************"
echo "All RANPM uninstallation scripts executed successfully!"
echo "*****************************************************************"
echo "*****************************************************************"