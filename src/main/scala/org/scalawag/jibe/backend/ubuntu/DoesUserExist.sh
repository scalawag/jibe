PATH=/bin:/usr/bin
IFS=: read -a parts <<< $( getent passwd "$user_name" )
if [ ${#parts[*]} -eq 0 ]; then
  echo "user $user_name does not exist"
  exit 1
else
  function compare {
    attr=$1
    desired=$2
    actual=$3
    if [ -n "$desired" -a "$actual" != "$desired" ]; then
      echo "$attr: $actual != $desired"
      mismatches=$(( mismatches + 1 ))
    fi
  }

  mismatches=0

  gname=$( awk -F: '$3 == '${parts[3]}' { print $1 }' /etc/group )
  compare group   "$user_primaryGroup" "$gname"
  compare uid     "$user_uid"          "${parts[2]}"
  compare comment "$user_comment"      "${parts[4]}"
  compare home    "$user_home"         "${parts[5]}"
  compare shell   "$user_shell"        "${parts[6]}"

  exit $mismatches
fi
