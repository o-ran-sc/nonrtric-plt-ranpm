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

# Build image from Dockerfile with/without custom image tag
# Optionally push to external docker hub repo

print_usage() {
    echo "Usage: build.sh no-push|<docker-hub-repo-name> [<image-tag>]"
    exit 1
}

if [ $# -ne 1 ] && [ $# -ne 2 ]; then
    print_usage
fi

IMAGE_NAME="pm-https-server"
IMAGE_TAG="latest"
REPO=""
if [ $1 == "no-push" ]; then
    echo "Only local image build"
else
    REPO=$1
    echo "Attempt to push built image to: "$REPO
fi

if [ "$2" != "" ]; then
    IMAGE_TAG=$2
fi
 echo "Setting image tag to: "$IMAGE_TAG

IMAGE=$IMAGE_NAME:$IMAGE_TAG
echo "Building image $IMAGE"
docker build -t $IMAGE_NAME:$IMAGE_TAG .
if [ $? -ne 0 ]; then
    echo "BUILD FAILED"
    exit 1
fi
echo "BUILD OK"

if [ "$REPO" != "" ]; then
    echo "Tagging image"
    NEW_IMAGE=$REPO/$IMAGE_NAME:$IMAGE_TAG
    docker tag $IMAGE $NEW_IMAGE
    if [ $? -ne 0 ]; then
        echo "RE-TAGGING FAILED"
        exit 1
    fi
    echo "RE-TAG OK"

    echo "Pushing image $NEW_IMAGE"
    docker push $NEW_IMAGE
    if [ $? -ne 0 ]; then
        echo "PUSHED FAILED"
        echo " Perhaps not logged into docker-hub repo $REPO?"
        exit 1
    fi
    IMAGE=$NEW_IMAGE
    echo "PUSH OK"
fi

echo "IMAGE OK: $IMAGE"
echo "DONE"
