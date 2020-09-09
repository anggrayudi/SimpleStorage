# Copy secret key ring for file signature
echo "$SECRET_KEY_RING_FILE_BASE_64" | base64 --decode >"$HOME/"secring.gpg
echo -e "\nsigning.secretKeyRingFile=$HOME/secring.gpg" >>gradle.properties
echo "signing.keyId=$SIGNING_KEY_ID" >>gradle.properties
echo "signing.password=$SIGNING_PASSWORD" >>gradle.properties
# Copy secret keys into gradle.properties
echo "SONATYPE_NEXUS_USERNAME=$OSS_SONATYPE_NEXUS_USERNAME" >>gradle.properties
echo "SONATYPE_NEXUS_PASSWORD=$OSS_SONATYPE_NEXUS_PASSWORD" >>gradle.properties
