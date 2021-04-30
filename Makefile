.PHONY: jar install deploy clean

SOURCES := $(shell find src)

target/tools-cli-pom2project.jar: deps.edn $(SOURCES)
	clojure -X:jar

jar: target/tools-cli-pom2project.jar

install: target/tools-cli-pom2project.jar
	clojure -X:install

deploy: target/tools-cli-pom2project.jar
	clojure -X:deploy

clean:
	rm -rf target
	rm -f pom.xml
