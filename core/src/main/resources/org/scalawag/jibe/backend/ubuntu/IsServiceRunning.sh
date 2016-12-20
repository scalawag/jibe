#!/usr/bin/env bash
PATH=/bin:/usr/sbin:/usr/bin

# Validate the input:
# 1. Complain if the service_name is empty
# 2. Complain service_name doesn't exist
# Next, if services_name isn't running, exit 1
# Else exit 0
if [[ "" == "$service_name" ]]; then
    echo "Error: No service_name provided"
    exit 2
elif ! service --status-all 2>&1 | grep -Fq $service_name > /dev/null; then
    echo "No service named $service_name"
    exit 2
elif ! service $service_name status; then
    echo "$service_name is not running"
    exit 1
else
    echo "$service_name is running"
    exit 0
fi