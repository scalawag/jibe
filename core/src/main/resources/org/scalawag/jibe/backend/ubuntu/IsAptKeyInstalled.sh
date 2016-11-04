PATH=/usr/bin:/bin
set -o pipefail
apt-key finger | grep fingerprint | sed -e 's/.* = //' -e 's/ //g' | grep "$fingerprint"
