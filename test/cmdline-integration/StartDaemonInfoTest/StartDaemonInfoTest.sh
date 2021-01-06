java -jar $SAKER_BUILD_JAR_PATH daemon start
if ! $? ; then 
	echo "Failed to start daemon"
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt

if ! grep -q "Daemon running at" proc_output.txt; then
	echo "Running daemon was not found."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon stop
exit $?
