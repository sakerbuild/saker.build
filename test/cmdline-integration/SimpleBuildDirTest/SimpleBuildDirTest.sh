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
