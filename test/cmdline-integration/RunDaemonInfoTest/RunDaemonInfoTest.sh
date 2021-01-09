# display commands
set -x
# exit status should be from the first failed command in a pipe
set -o pipefail

java -jar $SAKER_BUILD_JAR_PATH daemon run &
# wait for it to start up, should be enough
sleep 3

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt
if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon info -address localhost | tee proc_output.txt
if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found (-address localhost)."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon info -address localhost:3500 | tee proc_output.txt
if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found (-address localhost:3500)."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon info -address :3500 | tee proc_output.txt
if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found (-address :3500)."
	java -jar $SAKER_BUILD_JAR_PATH daemon stop
	exit 1
fi

java -jar $SAKER_BUILD_JAR_PATH daemon stop
exit $?
