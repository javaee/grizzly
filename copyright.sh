valid() {
	echo $* | \
		 grep -vi .apt | \
		 grep -vi .args | \
		 grep -vi .bundle | \
		 grep -vi .class | \
		 grep -vi .gif | \
		 grep -vi .ico | \
		 grep -vi .iml | \
		 grep -vi .ipr | \
		 grep -vi .iws | \
		 grep -vi .jar | \
		 grep -vi .jks | \
		 grep -vi .jpg | \
		 grep -vi .js | \
		 grep -vi .mm | \
		 grep -vi .ods | \
		 grep -vi .png | \
		 grep -vi .project | \
		 grep -vi copyright.sh | \
		 grep -vi copyrightcheck.out | \
		 grep -vi license.txt | \
		 grep -vi manifest.mf | \
		 grep -vi readme.txt | \
		 grep -vwi readme | \
		 grep -vi Grizzly-Migration-Guide | \
		 grep -vi zzzzzzzzzzzzzzzzzzz
}

run() {
	$JAVA -jar copyright.jar  $* | while read LINE
	do
		valid $LINE
	done
}

JAVA=/files/hudson/tools/java1.6/bin/java
if [ ! -f $JAVA ]
then
	JAVA=java
fi

run $* | tee copyrightcheck.out

grep -vi Copyright copyrightcheck.out | grep -v copyrightcheck.out > /dev/null
