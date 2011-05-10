#! /bin/sh
#
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
#
# Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
#
# The contents of this file are subject to the terms of either the GNU
# General Public License Version 2 only ("GPL") or the Common Development
# and Distribution License("CDDL") (collectively, the "License").  You
# may not use this file except in compliance with the License.  You can
# obtain a copy of the License at
# https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
# or packager/legal/LICENSE.txt.  See the License for the specific
# language governing permissions and limitations under the License.
#
# When distributing the software, include this License Header Notice in each
# file and include the License file at packager/legal/LICENSE.txt.
#
# GPL Classpath Exception:
# Oracle designates this particular file as subject to the "Classpath"
# exception as provided by Oracle in the GPL Version 2 section of the License
# file that accompanied this code.
#
# Modifications:
# If applicable, add the following below the License Header, with the fields
# enclosed by brackets [] replaced by your own identifying information:
# "Portions Copyright [year] [name of copyright owner]"
#
# Contributor(s):
# If you wish your version of this file to be governed by only the CDDL or
# only the GPL Version 2, indicate your decision by adding "[Contributor]
# elects to include this software in this distribution under the [CDDL or GPL
# Version 2] license."  If you don't indicate a single choice of license, a
# recipient has the option to distribute your version of this file under
# either the CDDL, the GPL Version 2 or to extend the choice of license to
# its licensees as provided above.  However, if you add GPL Version 2 code
# and therefore, elected the GPL Version 2 license, then the option applies
# only if the new code is made subject to such option by the copyright
# holder.
#

DRYRUN=false
growl=$(which growlnotify)

notify() {
    if [ -e "$growl" -a -x "$growl" ]
    then
       ${growl} -s -m "${1}"
    fi
    echo "\n\n${1}\n"
}


parse() {
	while [ "$1" ]
	do
		ARGS=(`echo $1 | tr '=' ' '`) 
		shift
		case ${ARGS[0]:2} in
			"tag")
				BRANCH=${ARGS[1]}
				;;
			"release_ver")
				RELEASE_VER=${ARGS[1]}
				;;
			"dev_ver")
				DEV_VER=${ARGS[1]}
				;;
			"user")
				JN_USER=${ARGS[1]}
				;;
			"password")
				JN_PWD=${ARGS[1]}
				;;
			"prepare")
				PREPARE="release:clean release:prepare"
				;;
		esac
	done
}

verify() {
	VAR=$(eval echo \$$1)
	if [ -z "${VAR}" ]
	then
		echo $2
		MISSING=true
	fi
}

parse $*
verify BRANCH "Missing --tag option (e.g., 1_2_3)"
verify RELEASE_VER "Missing --release_ver option (e.g., 1.2.3)"
verify DEV_VER "Missing --dev_ver option (e.g., 1.2.4-SNAPSHOT)"
verify JN_USER "Missing --user option for your java.net user name"
verify JN_PWD "Missing --password option for your java.net password"
[ "${MISSING}" ] && exit

if [ -z "${PREPARE}" ]
then
        SCM_URL="-DconnectionUrl=scm:git:ssh://${JN_USER}@git.java.net/grizzly~git"
fi

if [ "${PREPARE}" ]
then
	CMD="mvn -Darguments=-Dmaven.test.skip.exec=true -B -e -P release-profile -DdryRun=$DRYRUN -DautoVersionSubmodules=true -DdevelopmentVersion=${DEV_VER} -DreleaseVersion=${RELEASE_VER} -Dtag=${BRANCH} -Dpassword=${JN_PWD} -Dusername=${JN_USER} ${PREPARE}"

	echo ${CMD}
	eval ${CMD}
fi

CMD="mvn -Darguments=-Dmaven.test.skip.exec=true -B -e -P release-profile -DdryRun=$DRYRUN -DautoVersionSubmodules=true -DdevelopmentVersion=${DEV_VER} -DreleaseVersion=${RELEASE_VER} -Dtag=${BRANCH} -Dpassword=${JN_PWD} -Dusername=${JN_USER} ${SCM_URL} release:perform"

echo ${CMD}
eval ${CMD}

if [ $? -ne 0 ]
then
    notify "Release of Grizzly ${RELEASE_VER} failed!!"
    exit 1
else
    notify "Release of Grizzly ${RELEASE_VER} complete!"
fi

