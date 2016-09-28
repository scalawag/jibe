PATH=/usr/sbin
declare -a opts
opts+=(${user_uid:+-u "$user_uid"})
opts+=(${user_primaryGroup:+-g "$user_primaryGroup"})
opts+=(${user_home:+-d "$user_home"})
opts+=(${user_shell:+-s "$user_shell"})
opts+=(${user_comment:+-c "$user_comment"})

useradd "${opts[@]}" ${user_system:+-r} "${user_name}"
useradd_exit=$?
if [ $useradd_exit -eq 9 ]; then
  if [ -n "$opts" ]; then
    usermod "${opts[@]}" "${user_name}"
  else
    exit 0
  fi
else
  exit $useradd_exit
fi
