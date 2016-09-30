PATH=/bin:/usr/sbin:/usr/bin

lastUpdatedTime=$(stat -c %Y /var/cache/apt/pkgcache.bin)
currentTime="$(date +'%s')"
currentCacheAge=$(( currentTime - lastUpdatedTime ))
refreshIntervalInSeconds=$(( refreshInterval / 1000 ))

echo "Current cache age is $currentCacheAge"
echo "Maximum acceptable cache age (in seconds) is $refreshIntervalInSeconds"

if [ "${currentCacheAge}" -gt "$refreshIntervalInSeconds" ]; then
    echo "Running apt-get update"
    apt-get update
fi