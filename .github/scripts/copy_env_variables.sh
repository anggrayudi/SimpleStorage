# Append suffix -SNAPSHOT
if [[ ! ($(grep "STORAGE_VERSION=" gradle.properties) == *"-SNAPSHOT") ]]; then
  sed -ie "s/STORAGE_VERSION.*$/&-SNAPSHOT/g" gradle.properties
  rm -f gradle.propertiese
fi

mkdir "$HOME/.android"
keytool -genkey -v -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "C=US, O=Android, CN=Android Debug"

# Copy secret keys into gradle.properties
echo "mavenCentralUsername=$OSS_SONATYPE_NEXUS_USERNAME" >>gradle.properties
echo "mavenCentralPassword=$OSS_SONATYPE_NEXUS_PASSWORD" >>gradle.properties
