#!/bin/bash

ROOT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
cd "${ROOT_DIR}"

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