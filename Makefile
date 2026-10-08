clean:
	./mvnw clean

# Rebuild the front-end files in target/classes/static/vendor, e.g. after adding
# an icon while running from the IDE. Every Maven build does this anyway (spec 9.1).
assets:
	./mvnw -q generate-resources

build:
	./mvnw package

check-mvn-updates:
	./mvnw versions:display-dependency-updates

# Release v=x.y.z: dates the [Unreleased] changes in CHANGELOG.md as that
# version, and commits them with whatever else is staged for the release.
git-release:
	@test -n "$(v)" || { echo "usage: make git-release v=x.y.z" >&2; exit 1; }
	scripts/release-changelog.sh $(v)
	git add CHANGELOG.md
	git commit -m "Release $(v)"
	git tag -a -m "Release $(v)" v$(v)
	git push origin main
	git push origin v$(v)
