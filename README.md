# Commodity Price Economic Indicator System

A web application for correlating commodity prices with other macroeconomic data.
It's composed of three top-level components:

* Basic web application
* Data analyzer (background worker)
* Data collector (background worker)

#### Contents

1. [Quickstart](#quickstart)
2. [Project Stack](#project-stack)
3. [Development](#development)

---

## Quickstart

...

## Project Stack

This project is based on a [template by initialcapacity](https://github.com/initialcapacity/kotlin-ktor-starter). 

The codebase is written in a language called [Kotlin](https://kotlinlang.org) that is able to run on the JVM with full Java compatibility.
It uses the [Ktor](https://ktor.io) web framework, and runs on the [Netty](https://netty.io/) web server.
HTML templates are written using [Freemarker](https://freemarker.apache.org).
The codebase is tested with [JUnit](https://junit.org/) and uses [Gradle](https://gradle.org) to build a jarfile.
The [pack cli](https://buildpacks.io/docs/tools/pack/) is used to build a [Docker](https://www.docker.com/) container which is deployed to
[Google Cloud](https://cloud.google.com/) on Google's Cloud Platform.

### Web UI

See the `web/` directory for its own README file.

## Development

1.  Build a Java Archive (jar) file.
    ```bash
    ./gradlew clean build
    ```

2. Configure the port that each server runs on.
    ```bash
    export PORT=8881
    ```

Run the servers locally using the below examples.

### All In One

This is optimized for cheap deployment to Google's Cloud Run offering.

```bash
java -jar applications/single-process-app/build/libs/single-process-app-1.0-SNAPSHOT.jar
```

### Web application

```bash
java -jar applications/basic-server/build/libs/basic-server-1.0-SNAPSHOT.jar
```

### Data collector

```bash
java -jar applications/data-collector-server/build/libs/data-collector-server-1.0-SNAPSHOT.jar
```

### Data analyzer

```bash
java -jar applications/data-analyzer-server/build/libs/data-analyzer-server-1.0-SNAPSHOT.jar
```

## Production

Build a Docker container and run with Docker.

...

That's a wrap for now.
