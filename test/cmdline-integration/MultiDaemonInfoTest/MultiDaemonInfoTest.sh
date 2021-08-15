# display commands
set -x
# exit status should be from the first failed command in a pipe
set -o pipefail

java -jar $SAKER_BUILD_JAR_PATH daemon start -port 3500
if [ $? -ne 0 ] ; then 
	echo "FAIL: Failed to start daemon on port 3500"
	exit 1
fi
java -jar $SAKER_BUILD_JAR_PATH daemon start -port 3501
if [ $? -ne 0 ] ; then 
	echo "FAIL: Failed to start daemon on port 3501"
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt
if ! grep -q "Listening port: 3500" proc_output.txt; then
	echo "FAIL: Running daemon on port 3500 was not found."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop all
	exit 1
fi
if ! grep -q "Listening port: 3501" proc_output.txt; then
	echo "FAIL: Running daemon on port 3501 was not found."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop all
	exit 1
fi


java -jar $SAKER_BUILD_JAR_PATH daemon stop all
if [ $? -ne 0 ] ; then 
	echo "FAIL: Failed to stop all daemons"
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt
if ! grep -q "No daemon running" proc_output.txt; then
	echo "FAIL: Still found running daemons."
	exit 1
fi
