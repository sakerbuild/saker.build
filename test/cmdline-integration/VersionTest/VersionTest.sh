# display commands
set -x

java -jar $SAKER_BUILD_JAR_PATH version | tee proc_output.txt
if [ $? -ne 0 ] ; then 
	echo "FAIL: Failed query version"
	exit 1
fi
