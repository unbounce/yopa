#!/bin/bash
CONFIG_FILE=$1
if [ -z "$CONFIG_FILE" ]; then
    echo "Missing argument..."
    echo "Usage:'create_config.sh <path to yopa config>'"
    exit 1
fi
if [ ! -f $CONFIG_FILE ]; then
    echo "Yopa configuration file '$CONFIG_FILE' does not exist."
    exit 1
fi

kubectl create configmap yopa-config --from-file=yopa-config.yml=$CONFIG_FILE
