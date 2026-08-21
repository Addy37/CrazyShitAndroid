# Security Policy

## Supported version

Only the latest published GitHub Release is considered supported.

## Reporting a wrapper security issue

If you find a security issue in the Android wrapper itself, open a GitHub issue without including passwords, private account data, signing keys, or other secrets.

For a serious issue that would expose user data or permit code execution, avoid posting exploit details publicly until the maintainer has had a chance to review the report.

## Website security issues

This project does not operate CrazyShit.com. Security, account, content, or privacy issues affecting the website itself should be reported through CrazyShit.com's official contact channels.

## Release signing

Public release APKs are intended to be signed with a private Android release key stored only in GitHub Actions secrets and in the maintainer's private backup. The signing key must never be committed to the repository.

Users should prefer APKs attached to official GitHub Releases in this repository rather than APKs re-uploaded by third parties.