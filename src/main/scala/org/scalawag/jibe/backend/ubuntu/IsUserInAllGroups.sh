PATH=/bin:/usr/bin
existingGroups=$( groups "$targetUser" )
for testGroup in $targetGroups; do
  echo $existingGroups >&2
  echo $existingGroups | grep -w $testGroup
echo "Here's something for stdout."
echo "Here's something for stderr." >&2
echo "Here's a little more for stdout."
  if [ $? != 0 ]; then
    exit 1
  fi
done
