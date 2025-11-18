GRADLEW = ./gradlew
JAR_FILE = build/libs/lit-mcp-1.0-SNAPSHOT.jar

build:
	$(GRADLEW) spotlessApply bootJar

run: build
	java -jar $(JAR_FILE) --transport=http

npx_run:
	npx @modelcontextprotocol/inspector

test:
	$(GRADLEW) test

clean:
	$(GRADLEW) clean

.PHONY: build run clean
