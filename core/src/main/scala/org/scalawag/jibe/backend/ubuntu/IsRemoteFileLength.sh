PATH=/usr/bin
set -e
actual=$( stat -c %s "$file" )
if [ $actual -ne $length ]; then
  echo "$actual != $length"
  exit 1
fi
