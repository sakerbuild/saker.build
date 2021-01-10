export SAKER_BUILD_JAR_PATH=$(realpath $1)

# kill possible running processes that can interfere with the tests
killall -q java

for f in **/*Test.sh
do
	echo "---------- Run test $f"
	(cd $(dirname $f) && bash $(basename $f))
	retVal=$?
	# kill possible leftover processes before the test
	killall -q java
	if [ $retVal -ne 0 ]; then
	    echo "---------- Test failed: $f"
		exit $retVal
	fi
	# kill possible leftover processes after the test
	killall -q java
	echo "---------- Test success: $f"
	echo ""
done
