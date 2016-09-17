PATH=/usr/sbin
opts="${group_gid:+-g $group_id}"

groupadd $opts ${user_system:+-s} ${group_name}
if [ $? -eq 9 ]; then
  if [ -n "$opts" ]; then
    groupmod $opts ${group_name}
  else
    exit 0
  fi
fi
