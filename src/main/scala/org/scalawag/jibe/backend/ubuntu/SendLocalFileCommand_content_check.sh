PATH=/usr/bin
if [ ! -r "$rpath" ]; then
  echo "remote file does not exist"
  exit 1
else
  rmd5=$( md5sum "$rpath" | awk '{ print $1 }' )
  if [ $lmd5 != $rmd5 ]; then
    echo "md5(local)  = $lmd5"
    echo "md5(remote) = $rmd5"
    exit 1
  fi
fi
