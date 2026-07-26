# Port application notes

The migration is committed on `1.21.1-neoforge` as one auditable replacement tree while retaining the fork's upstream commit history.

Promotion procedure:

1. Run the static audit and Java 21 build in GitHub Actions.
2. Correct all compile/Mixin errors on the migration branch.
3. Complete the release-gate tests in `TEST_MATRIX.md` on a client and dedicated server.
4. Fast-forward `master` only after the build is green; do not publish a release merely from compilation success.
