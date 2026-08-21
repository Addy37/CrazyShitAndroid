# Release Signing

Public Android releases must use the same private signing key for every version. This lets Android verify that future APK updates come from the same publisher.

## GitHub Actions secret

The release workflow expects one repository Actions secret named:

`ANDROID_SIGNING_BUNDLE`

Its value is a compact JSON object containing:

- `keystore_base64`
- `store_password`
- `key_alias`
- `key_password`

The actual key and secret values must never be committed to this repository.

## Release process

1. Keep a private offline backup of the release keystore.
2. Store the signing bundle only in the repository's encrypted Actions secrets.
3. Open **Actions > Publish Signed APK Release**.
4. Run the workflow and enter the release version, such as `1.0.0`.
5. The workflow builds, signs, verifies, hashes, and publishes the APK to GitHub Releases.

The release page includes a fixed asset name, `CrazyShit-Unofficial.apk`, so the latest-download URL remains stable.

## Key loss

If the private signing key is lost, future APKs cannot update installs signed by the old key. Users would have to uninstall the existing app before installing an APK signed by a new key.

## Security

Never publish the keystore, its passwords, the signing-bundle JSON, or screenshots containing those values.