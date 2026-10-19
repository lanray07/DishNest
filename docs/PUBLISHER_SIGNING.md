# DishNest publisher signing

O. Bankole explicitly authorized creation of the permanent release key on 19 October 2026. `tools/sign_publisher_release.ps1` initially created it in the Git-ignored `.local-signing` directory, then moved it with hash verification to `%LOCALAPPDATA%/DishNest/publisher-private/.local-signing` on NTFS. The project drive is exFAT and cannot enforce ACLs. The directory permits only the current Windows account and SYSTEM. The key password is generated from 48 random bytes and stored as a Windows DPAPI-encrypted PSCredential, never in Git, APKs, source archives or command-line arguments. The alias is `dishnest-release`.

The script refuses a partial setup and refuses a certificate change when prior evidence exists. Run it again to sign a newly verified unsigned build with the same key. It checks current build provenance, APK signature, certificate identity, 16 KB ZIP alignment and equality of every original ZIP payload. `tools/publisher_evidence.py` independently repeats the binary checks during packaging. Public fingerprint, validity and APK hashes are recorded in `artifacts/publisher-signing.json`.

## Required secure backup before release

Preserve `%LOCALAPPDATA%/DishNest/publisher-private/.local-signing/dishnest-release.jks` and the alias/password in a secure, encrypted backup under publisher control. Keep an independent recovery copy. Losing this identity can prevent future app updates.

The DPAPI `credentials.clixml` file can be decrypted only by the same Windows user on this computer. Copying that XML and the keystore alone is **not a portable recovery backup**. While this Windows account still works, the publisher can privately retrieve the credential using PowerShell's `Import-Clixml` and `GetNetworkCredential().Password`, then save it in a trusted password manager. Do not paste it into chat or a repository, or run that retrieval through an agent tool that records output. Verify the backup on a trusted recovery machine before submitting. Backup/recovery has not been certified.

## Hardware limitation

The connected Fire has `com.dishnest.app` signed with the Android debug certificate. Its certificate differs from the new permanent publisher certificate. Android cannot update that installation with the publisher-signed APK. Read-only inspection is recorded in `artifacts/publisher-device-compatibility.json`; the ordinary app was neither uninstalled nor cleared.

The isolated QA package passed its current smoke and timer checks separately. Those tests do not approve the exact publisher-signed production APK. A clean compatible device, or a publisher-approved data-preserving migration and removal of the debug installation, is still needed. The signed artifact also retains the verified build's disabled purchases: it must not be advertised or submitted as completed Premium billing. No upload or submission occurred.
