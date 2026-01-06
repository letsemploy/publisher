clean:
	./mvnw clean

update:
	npm update
	mkdir -p ./src/main/resources/static/css/fontawesome
	mkdir -p ./src/main/resources/static/js/
	cp -rf ./node_modules/@fortawesome/fontawesome-free/css/ ./src/main/resources/static/css/fontawesome/
	cp -rf ./node_modules/@fortawesome/fontawesome-free/webfonts ./src/main/resources/static/css/fontawesome/
	cp -rf ./node_modules/hyperscript.org/dist/_hyperscript.min.js ./src/main/resources/static/js/
	cp -rf ./node_modules/htmx.org/dist/htmx.min.js ./src/main/resources/static/js/
	cp -rf ./node_modules/@atlassian/aui/dist/aui/ ./src/main/resources/static/
	cp -rf ./node_modules/jquery/dist/jquery.min.js ./src/main/resources/static/js/
	cp -rf ./node_modules/chart.js/dist/chart.umd.js ./src/main/resources/static/js/chart.js
	cp -rf ./node_modules/chart.js/dist/chart.umd.js.map ./src/main/resources/static/js/chart.umd.js.map
	cp -rf ./node_modules/feather-icons/dist/feather-sprite.svg ./src/main/resources/static/img/
	cp -rf ./node_modules/@tabler/icons-sprite/dist/tabler-sprite.svg ./src/main/resources/static/img/
	cp -rf ./node_modules/lucide/dist/umd/lucide.min.js ./src/main/resources/static/js/

install: clean
	npm install

build: install update
	npm run css-build
	./mvnw package

check-mvn-updates:
	./mvnw versions:display-dependency-updates

git-release:
	git commit -m '"Release $(v)"'
	git tag -a -m '"Release $(v)"' v$(v)
	git push github main
	git push github v$(v)
