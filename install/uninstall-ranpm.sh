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
echo "All RANPM uninstallation scripts executed successfully!"
echo "*****************************************************************"
echo "*****************************************************************"