GRADLEW = ./gradlew
JAR_FILE = build/libs/lit-mcp-1.0.jar

build:
	$(GRADLEW) spotlessApply bootJar

run_http: build
	java -jar $(JAR_FILE) --transport=http

run_stdio: build
	java -jar $(JAR_FILE) --transport=stdio

run_npx:
	npx @modelcontextprotocol/inspector

test:
	$(GRADLEW) test

clean:
	$(GRADLEW) clean

.PHONY: build run clean
