PATH=/usr/bin
set -e
actualLength=$( stat -c %s "$file" )
if [ $actualLength -ne $length ]; then
  echo "$actualLength != $length"
  exit 1
fi
