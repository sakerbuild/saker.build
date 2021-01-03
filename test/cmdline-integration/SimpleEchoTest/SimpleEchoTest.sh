set -e

java -jar $SAKER_BUILD_JAR_PATH > build_output.txt

if grep -q "hello world" build_output.txt; then
	cat build_output.txt
	echo "Message not found"
	exit 1
fi
