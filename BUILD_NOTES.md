# Build Configuration Notes

## Java Version Requirement

This project requires **Java 17** to build and run successfully.

### Why Java 17?

The project was originally configured for Java 17, which is the baseline LTS version supported by Spring Boot 3.2.0. While the system may have Java 21 installed, we encountered compiler plugin compatibility issues (TypeTag::UNKNOWN errors) when attempting to build with Java 21.

### Solution

The project includes convenience scripts that automatically detect and use Java 17:

```bash
# Build the project
./build.sh

# Run the application
./run.sh
```

### Manual Build/Run

If you prefer to build/run manually:

```bash
# Set JAVA_HOME to Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Build
mvn clean install

# Run
mvn spring-boot:run
```

### Installing Java 17

If you don't have Java 17 installed:

**macOS (Homebrew):**
```bash
brew install --cask temurin@17
```

**Windows:**
- Download from [Adoptium](https://adoptium.net/temurin/releases/?version=17)

**Linux:**
```bash
# Ubuntu/Debian
sudo apt install openjdk-17-jdk

# RHEL/CentOS/Fedora
sudo yum install java-17-openjdk-devel
```

## Maven Configuration

The project uses:
- **maven-compiler-plugin 3.13.0** with Java 17 release target
- **Lombok 1.18.30** configured as an annotation processor
- **Spring Boot 3.2.0** parent POM

### Key pom.xml Settings

```xml
<properties>
    <java.version>17</java.version>
    <lombok.version>1.18.30</lombok.version>
</properties>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
            <configuration>
                <release>17</release>
                <annotationProcessorPaths>
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Troubleshooting

### "TypeTag::UNKNOWN" Error

If you see this error, you're likely using the wrong Java version. Ensure JAVA_HOME points to Java 17:

```bash
java -version  # Should show Java 17.x.x
echo $JAVA_HOME  # Should point to JDK 17
```

### Lombok Annotations Not Processed

If you see compilation errors about missing getters/setters, the Lombok annotation processor may not be configured correctly. Ensure:

1. Lombok dependency is in pom.xml
2. The maven-compiler-plugin has `annotationProcessorPaths` configured
3. Your IDE has Lombok plugin installed (for IDE support)

## Verification

After a successful build, you should see:

```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  XX.XXX s
[INFO] Finished at: YYYY-MM-DDTHH:MM:SS
[INFO] ------------------------------------------------------------------------
```

The application JAR will be created at:
```
target/ecommerce-monolith-1.0.0-SNAPSHOT.jar
```

## Running the Application

Once built, start the application:

```bash
./run.sh
```

Or manually:
```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run
```

The application will be available at:
- **REST API**: http://localhost:8080/api
- **H2 Console**: http://localhost:8080/h2-console

### Sample API Endpoints

```bash
# Get all customers
curl http://localhost:8080/api/customers

# Get all products  
curl http://localhost:8080/api/products

# Get all orders
curl http://localhost:8080/api/orders
```

See `API_EXAMPLES.md` for comprehensive API testing examples.
