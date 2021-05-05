# Append suffix -SNAPSHOT
if [[ ! ($(grep "STORAGE_VERSION=" gradle.properties) == *"-SNAPSHOT") ]]; then
  sed -ie "s/STORAGE_VERSION.*$/&-SNAPSHOT/g" gradle.properties
  rm -f gradle.propertiese
fi

mkdir "$HOME/.android"
echo "$DEBUG_KEYSTORE_BASE64" | base64 --decode >"$HOME/.android/debug.keystore"

# Copy secret key ring for file signature
echo "$SECRET_KEY_RING_FILE_BASE_64" | base64 --decode >"$HOME/secring.gpg"
echo -e "\nsigning.secretKeyRingFile=$HOME/secring.gpg" >>gradle.properties
echo "signing.keyId=$SIGNING_KEY_ID" >>gradle.properties
echo "signing.password=$SIGNING_PASSWORD" >>gradle.properties
# Copy secret keys into gradle.properties
echo "mavenCentralUsername=$OSS_SONATYPE_NEXUS_USERNAME" >>gradle.properties
echo "mavenCentralPassword=$OSS_SONATYPE_NEXUS_PASSWORD" >>gradle.properties
