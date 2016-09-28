PATH=/usr/bin
set -e
set -o pipefail
actual=$( md5sum "$file" | cut -d' ' -f1 )
if [ $actual != $md5 ]; then
  echo "$actual != $md5"
  exit 1
fi
exit 0

PATH=/usr/bin
if [ ! -r "$rpath" ]; then
  echo "remote file does not exist"
  exit 1
else
  rmd5=
  if [ $lmd5 != $rmd5 ]; then
    echo "md5(local)  = $lmd5"
    echo "md5(remote) = $rmd5"
    exit 1
  fi
fi
