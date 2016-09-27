PATH=/usr/sbin
userdel ${userName}
userdel_exit=$?
if [ $userdel_exit -eq 6 ]; then
  exit 0
else
  exit $userdel_exit
fi
