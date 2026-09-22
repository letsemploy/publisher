clean:
	./mvnw clean

# Refresh the vendored front-end assets from the pinned npm dependencies.
# The output is committed; ./mvnw package needs no Node (spec 9.1).
assets:
	npm ci
	npm run vendor

build:
	./mvnw package

check-mvn-updates:
	./mvnw versions:display-dependency-updates

git-release:
	git commit -m '"Release $(v)"'
	git tag -a -m '"Release $(v)"' v$(v)
	git push github main
	git push github v$(v)
