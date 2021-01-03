export SAKER_BUILD_JAR_PATH=$(realpath $1)

# kill possible running processes that can interfere with the tests
killall java

for f in **/*Test.sh
do
	echo "Run test $f"
	retVal=$?
	# kill possible leftover processes
	killall java
	if [ $retVal -ne 0 ]; then
	    echo "Test failed: $f"
		exit $retVal
	fi
	bash $f
done
