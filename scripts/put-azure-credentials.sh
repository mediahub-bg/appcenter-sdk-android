#!/bin/bash
cat >> local.properties << EOL
AZURE_USERNAME=${1:-$AZURE_USERNAME}
AZURE_PASSWORD=${2:-$AZURE_PASSWORD}
EOL