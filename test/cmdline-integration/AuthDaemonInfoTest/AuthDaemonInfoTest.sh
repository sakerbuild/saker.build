# display commands
set -x

# all commands should succeed
set -e

# generate the keys used for authentication
keytool -genkey -noprompt -dname "CN=server" -alias server_alias -keystore ca.jks -keyalg RSA -storepass testtest -keypass testtest -ext bc:c
keytool -keystore ca.jks -alias server_alias -exportcert -rfc -storepass testtest -keypass testtest > ca.pem
keytool -genkey -noprompt -dname "CN=client" -alias client_alias -keystore client.jks -keyalg RSA -storepass testtest -keypass testtest
keytool -keystore client.jks -alias client_alias -exportcert -rfc -storepass testtest -keypass testtest > client.pem
keytool -keystore client.jks -certreq -alias client_alias -keyalg rsa -file client.csr -storepass testtest -keypass testtest
keytool -gencert -keystore ca.jks -alias server_alias -storepass testtest -keypass testtest -infile client.csr -ext ku:c=dig,keyEncipherment -rfc -outfile signed.pem
cat ca.pem signed.pem > signed_chain.pem
cp client.jks client_signed.jks
keytool -noprompt -keystore client_signed.jks -importcert -alias client_alias -file signed_chain.pem -storepass testtest -keypass testtest

java -jar $SAKER_BUILD_JAR_PATH daemon start -auth-keystore ca.jks -auth-storepass testtest

# test the client signed keystore
java -jar $SAKER_BUILD_JAR_PATH daemon info -auth-keystore client_signed.jks -auth-storepass testtest | tee proc_output.txt
if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found."
	exit 1
fi
if ! grep -q "Daemon uses keystore for authentication" proc_output.txt; then
	echo "FAIL: Daemon doesn't report keystore."
	exit 1
fi

# test the ca keystore (same as the one the daemon uses)
java -jar $SAKER_BUILD_JAR_PATH daemon info -auth-keystore ca.jks -auth-storepass testtest | tee proc_output.txt
if ! grep -q "Daemon running at" proc_output.txt; then
	echo "FAIL: Running daemon was not found."
	exit 1
fi
if ! grep -q "Daemon uses keystore for authentication" proc_output.txt; then
	echo "FAIL: Daemon doesn't report keystore."
	exit 1
fi

# test the unsigned keystore, it shouldn't be accepted
set +e
java -jar $SAKER_BUILD_JAR_PATH daemon info -auth-keystore client.jks -auth-storepass testtest | tee proc_output.txt
if [ $? = 0 ] ; then 
	echo "FAIL: Shouldn't be able to connect to daemon"
	exit 1
fi
# SSLHandshakeException is expected
if ! grep -q "SSLHandshakeException" proc_output.txt; then
	echo "FAIL: Didn't receive SSLHandshakeException."
	exit 1
fi
set -e


java -jar $SAKER_BUILD_JAR_PATH daemon stop -auth-keystore client_signed.jks -auth-storepass testtest