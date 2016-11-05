PATH=/usr/bin:/bin
set -o pipefail
apt-key finger | sed -ne '/.*Key fingerprint =/ { s/.* = // ; s/ //g ; p }' | grep -x "$fingerprint" > /dev/null
ecs=("${PIPESTATUS[@]}")
# If anything but the grep failed (last command), it's an error. Exit with 2.
( for ec in ${ecs[*]}; do echo $ec; done | head -n -1 | grep -x -v 0 > /dev/null ) && exit 2
# Otherwise, exit with whatever grep exited with.
exit ${ecs[-1]}
