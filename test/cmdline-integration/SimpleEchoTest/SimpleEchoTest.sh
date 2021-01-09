# display commands
set -x

set -e

java -jar $SAKER_BUILD_JAR_PATH | tee build_output.txt

if ! grep -q "hello world" build_output.txt; then
	echo "FAIL: Printed message not found."
	exit 1
fi
