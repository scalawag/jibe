#!/usr/bin/env bash
PATH=/bin:/usr/sbin:/usr/bin:/sbin

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
elif service $service_name status | grep "start"; then
    echo "$service_name is running"
    exit 0
elif ! service $service_name status | grep "start"; then
    echo "$service_name is not running"
    exit 1
fi

