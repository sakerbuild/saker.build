# display commands
set -x
# exit status should be from the first failed command in a pipe
set -o pipefail

java -jar $SAKER_BUILD_JAR_PATH daemon info | tee proc_output.txt

if ! grep -q "No daemon" proc_output.txt; then
	echo "FAIL: A running daemon was found."
	exit 1
fi
