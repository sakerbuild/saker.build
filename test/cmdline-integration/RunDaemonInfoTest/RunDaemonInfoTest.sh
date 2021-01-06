java -jar $SAKER_BUILD_JAR_PATH daemon run &
# wait for it to start up, should be enough
sleep 3

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt

if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon stop
exit $?
