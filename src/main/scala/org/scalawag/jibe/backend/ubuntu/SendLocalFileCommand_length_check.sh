PATH=/usr/bin
if [ ! -r "$rpath" ]; then
  echo "remote file does not exist"
  exit 1
else
  rlen=$( stat -c %s "$rpath" )
  if [ $llen != $rlen ]; then
    echo "len(local)  = $llen"
    echo "len(remote) = $rlen"
    exit 1
  fi
fi
