PATH=/usr/sbin:/usr/bin
usermod -G $( echo $targetGroups | tr ' ' ',' ) -a $targetUser