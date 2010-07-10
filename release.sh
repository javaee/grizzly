#! /bin/sh

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
				SVN_USER=${ARGS[1]}
				;;
			"password")
				SVN_PWD=${ARGS[1]}
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
verify SVN_USER "Missing --user option for your svn user name"
verify SVN_PWD "Missing --password option for your svn password"
[ "${MISSING}" ] && exit

if [ -z "${PREPARE}" ] 
then
	SVN_URL="-DconnectionUrl=scm:svn:https://grizzly.dev.java.net/svn/grizzly/tags/${BRANCH}"
fi

CMD="mvn -Darguments=-Dmaven.test.skip=true -e -P release-profile -DdryRun=$DRYRUN -DautoVersionSubmodules=true -DdevelopmentVersion=${DEV_VER} -DreleaseVersion=${RELEASE_VER} -Dtag=${BRANCH} -Dpassword=${SVN_PWD} -Dusername=${SVN_USER} ${PREPARE} ${SVN_URL} release:perform"

echo ${CMD}
eval ${CMD}

if [ $? -ne 0 ]
then
    notify "Release of Grizzly ${RELEASE_VER} failed!!"
    exit 1
else
    notify "Release of Grizzly ${RELEASE_VER} complete!"
fi

