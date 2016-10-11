PATH=/usr/bin
set -e
set -o pipefail
actualMd5=$( md5sum "$file" | cut -d' ' -f1 )
if [ $actualMd5 != $md5 ]; then
  echo "$actualMd5 != $md5"
  exit 1
fi
exit 0
