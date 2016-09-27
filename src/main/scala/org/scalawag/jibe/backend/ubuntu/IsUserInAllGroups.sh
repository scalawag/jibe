PATH=/bin:/usr/bin
existingGroups=$( groups "$targetUser" )
for testGroup in $targetGroups; do
  echo $existingGroups | grep -w $testGroup
  if [ $? != 0 ]; then
    exit 1
  fi
done
