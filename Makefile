GRADLEW = ./gradlew
JAR_FILE = build/libs/lit-mcp-1.0-SNAPSHOT.jar

build:
	$(GRADLEW) bootJar

run: build
	java -jar $(JAR_FILE) --transport=http

npx_run:
	npx @modelcontextprotocol/inspector

clean:
	$(GRADLEW) clean

.PHONY: build run clean
