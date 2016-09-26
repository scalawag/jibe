PATH=/usr/sbin
opts="${group_gid:+-g $group_gid}"

groupadd $opts ${group_system:+-r} "${group_name}"
groupadd_exit=$?
if [ $groupadd_exit -eq 9 ]; then
  if [ -n "$opts" ]; then
    groupmod $opts ${group_name}
  else
    exit 0
  fi
else
  exit $groupadd_exit
fi
