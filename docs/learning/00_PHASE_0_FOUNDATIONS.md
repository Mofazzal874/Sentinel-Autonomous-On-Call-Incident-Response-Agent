# Phase 0 Learning Note: Java, Gradle, and Spring Boot Foundations

## What Phase 0 proved

Before databases, queues, security, or AI, the project proved that one command can compile the code and start a Spring application context consistently:

```powershell
.\gradlew.bat clean test
```

This small result establishes the foundation every later feature depends on.

## Java Development Kit

The JDK supplies the Java compiler (`javac`), runtime (`java`), debugger, and standard libraries.

Sentinel uses Java 25. Two settings are related but different:

- `JAVA_HOME` tells tools which installed JDK to start with.
- The Gradle Java toolchain declares which Java version the project must compile and test with.

Example mental model:

```text
JAVA_HOME -> starts Gradle on Java 25
Gradle toolchain -> compiles Sentinel as Java 25
```

## Gradle and the wrapper

Gradle is the build system. It resolves dependencies, compiles source, runs tests, and packages the application.

The wrapper consists of `gradlew`, `gradlew.bat`, and `gradle/wrapper/*`. It pins the Gradle version for the repository. A teammate does not need to guess which global Gradle version to install.

Important files:

- `settings.gradle.kts`: names the Gradle project.
- `build.gradle.kts`: declares plugins, Java version, repositories, dependencies, and task configuration.
- `gradle.properties`: controls build caching, parallelism, and other Gradle behavior.

## What Spring Boot adds

Plain Spring provides dependency injection and application infrastructure. Spring Boot adds sensible defaults and automatic configuration based on dependencies and properties.

Startup flow:

```text
SentinelApplication.main
        |
        v
SpringApplication.run
        |
        v
create ApplicationContext
        |
        v
scan for components and create beans
        |
        v
apply auto-configuration
        |
        v
start the embedded web server
```

The `@SpringBootApplication` annotation combines three ideas:

- Configuration: this class can declare application configuration.
- Auto-configuration: Spring Boot configures facilities detected on the classpath.
- Component scanning: Spring finds application components under the package tree.

## Dependency injection in plain language

A class should declare what it needs instead of constructing every dependency itself.

Without dependency injection:

```java
class AlertService {
    private final AlertRepository repository = new PostgresAlertRepository();
}
```

The service is now hard-wired to one database implementation and difficult to test.

With constructor injection:

```java
class AlertService {
    private final AlertRepository repository;

    AlertService(AlertRepository repository) {
        this.repository = repository;
    }
}
```

Spring creates the objects and supplies the selected repository. A unit test can supply a fake repository instead.

## What is a bean?

A bean is an object whose creation and lifecycle are managed by the Spring application context. Controllers, services, repositories, configuration objects, and infrastructure clients commonly become beans.

This does not make the object magical. Spring still calls a Java constructor; it simply centralizes object assembly and lifecycle management.

## The context smoke test

`@SpringBootTest` asks Spring Boot to create the application context during a test. The empty `contextLoads()` test succeeds only if configuration and bean creation complete without an exception.

It proves wiring, not business behavior. Later tests must make precise assertions about domain rules, queries, security, and failure handling.

## Why no database was added yet

Adding everything immediately makes startup failure ambiguous. A failure could come from Java, Gradle, Spring, PostgreSQL, credentials, schema mismatch, or Docker. Phase 0 isolated the build/runtime foundation before infrastructure was introduced.

This is both a learning technique and a system-engineering technique: reduce the number of changing variables.

## Notebook exercises

1. Draw the startup flow from `main()` to the embedded server.
2. Explain the difference between a JDK, JVM, Gradle, Spring, and Spring Boot.
3. Why does the wrapper belong in Git but the Gradle cache does not?
4. What does dependency injection improve besides reducing `new` calls?
5. What does `contextLoads()` prove, and what does it fail to prove?
6. Predict what happens if the Java toolchain says 25 but only Java 20 is installed.

## Explain it aloud

Try this summary without reading:

> The JDK compiles and runs Java. Gradle provides a reproducible build and dependency graph. Spring owns object assembly through an application context. Spring Boot examines the classpath and properties to configure common infrastructure, then starts the embedded server. Phase 0 proved that baseline before adding external systems.
