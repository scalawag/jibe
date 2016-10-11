PATH=/usr/sbin:/usr/bin
usermod -G $( IFS=, ; echo "${groups[*]}" ) -a $user