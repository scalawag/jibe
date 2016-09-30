#!/usr/bin/env bash
PATH=/bin:/usr/sbin:/usr/bin

# First validate the input:
# 1. Complain if the pkg_name is empty
# 2. Complain if the a package of that name doesn't exist
if [[ "" == "$pkg_name" ]]; then
    echo "Error: No pkg_name provided"
    exit 2
elif [[ "" == $(apt-cache search --names-only "^$pkg_name$" | grep "^$pkg_name -") ]]; then
    echo "No package named $pkg_name found in apt-cache search"
    exit 2
fi

# Find out if the package is already installed, what version it is, and what the next available version is:
CURRENT_PACKAGE_VERSION=$(dpkg-query -W --showformat='${Version}\n' $pkg_name 2> /dev/null)
CANDIDATE_PACKAGE_VERSION=$(apt-cache policy $pkg_name | grep Candidate | sed "s/  Candidate: //" )

# Return 1 if we need to install this package, i.e.:
# 1. If the package is not installed
# 2. If a target package version was passed in and the current version doesn't match it
# 3. If a new version is available that's greater than the current version
if [[ "" == "$CURRENT_PACKAGE_VERSION" || \
      ( "$pkg_version" != "" && 0 -eq $(dpkg --compare-versions "$pkg_version" "ne" "$CURRENT_PACKAGE_VERSION"; echo $?)) || \
      ( "$pkg_version" == "" && 1 -eq $(dpkg --compare-versions "$CURRENT_PACKAGE_VERSION" "le" "$CANDIDATE_PACKAGE_VERSION"; echo $?)) ]]; then
    echo "$pkg_name is needed"
    exit 1
else
    echo "$pkg_name is already installed at $CANDIDATE_PACKAGE_VERSION"
fi
