PATH=/bin:/usr/bin
existingGroups=$( groups "$user" )
for testGroup in "${groups[@]}"; do
  echo $existingGroups | grep -w $testGroup
  if [ $? != 0 ]; then
    exit 1
  fi
done
