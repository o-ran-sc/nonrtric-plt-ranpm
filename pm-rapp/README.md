

## Basic rAPP for demo purpose - starts a subscription and prints out received data on the topic to stdout


### Manual build, tag and push to image repo

Build for docker or local kubernetes\
`./build.sh no-push [<image-tag>]`

Build for remote kubernetes - an externally accessible image repo (e.g. docker hub) is needed  \
`./build.sh <external-image-repo> [<image-tag>]`


### Configuration

