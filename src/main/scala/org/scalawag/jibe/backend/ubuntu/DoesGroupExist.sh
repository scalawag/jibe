PATH=/usr/bin
set -e
gid=$( awk -F: '$1 == "'$group_name'" { print $3 }' /etc/group )
if [ -z "$gid" ]; then
  echo "group $group_name does not exist"
  exit 1
elif [ -n "$group_gid" -a "$group_gid" != "$gid" ]; then
  echo "desired gid: $group_gid"
  echo " actual gid: $gid"
  exit 1
fi
