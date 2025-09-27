GRADLEW = ./gradlew
JAR_FILE = build/libs/lit-mcp-1.0-SNAPSHOT.jar

build:
	$(GRADLEW) bootJar

run: build
	java -jar $(JAR_FILE) --transport=http

clean:
	$(GRADLEW) clean

.PHONY: build run clean
