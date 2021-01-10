# display commands
set -x
# exit status should be from the first failed command in a pipe
set -o pipefail
# all commands should succeed
set -e

java -jar $SAKER_BUILD_JAR_PATH -bd build | tee build_output.txt

if ! grep -q "hello world" build_output.txt; then
	echo "FAIL: Printed message not found."
	exit 1
fi

if [ ! -f build/dependencies.map ]; then
	ls -la build
    echo "FAIL: Dependencies file doesn't exist"
    exit 1
fi
