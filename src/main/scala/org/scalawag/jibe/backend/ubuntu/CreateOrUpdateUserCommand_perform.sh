PATH=/usr/sbin
opts="${opts}${user_uid:+-u $user_uid }"
opts="${opts}${user_group:+-g $user_group }"
opts="${opts}${user_home:+-d $user_home }"
opts="${opts}${user_shell:+-s $user_shell }"
opts="${opts}${user_comment:+-c $user_comment }"

useradd $opts ${user_system:+-s} ${user_name}
if [ $? -eq 9 ]; then
  if [ -n "$opts" ]; then
    usermod $opts ${user_name}
  else
    exit 0
  fi
fi
