# display commands
set -x
# exit status should be from the first failed command in a pipe
set -o pipefail

java -jar $SAKER_BUILD_JAR_PATH version | tee proc_output.txt
if [ $? -ne 0 ] ; then 
	echo "FAIL: Failed query version"
	exit 1
fi
