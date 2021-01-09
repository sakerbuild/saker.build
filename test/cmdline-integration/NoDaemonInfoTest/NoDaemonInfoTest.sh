# display commands
set -x

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt

if ! grep -q "No daemon" proc_output.txt; then
	echo "FAIL: A running daemon was found."
	exit 1
fi
