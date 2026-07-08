#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK_DIR="${ROOT_DIR}/target/regression-samples"
SOURCES_DIR="${WORK_DIR}/sources"
RESTORE_DIR="${WORK_DIR}/restored"
REPORT_DIR="${WORK_DIR}/report"
JAR2MP_JAR="${ROOT_DIR}/target/jar2mp-1.0-jar-with-dependencies.jar"

MVN="${MVN:-mvn}"
JAVA_TRACE_TIMEOUT="${JAVA_TRACE_TIMEOUT:-30}"

mkdir -p "${SOURCES_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"

sample_names=()
sample_artifacts=()
sample_trace_modes=()
sample_thresholds=()
sample_notes=()

log() {
  printf '[regression] %s\n' "$*"
}

csv_field() {
  local value="$1"
  value="${value//\"/\"\"}"
  printf '"%s"' "${value}"
}

is_positive_integer() {
  [[ "$1" =~ ^[1-9][0-9]*$ ]]
}

write_file() {
  local path="$1"
  mkdir -p "$(dirname "${path}")"
  cat > "${path}"
}

run_maven() {
  local project_dir="$1"
  (cd "${project_dir}" && "${MVN}" -q -DskipTests package)
}

register_sample() {
  local name="$1"
  local artifact="$2"
  local trace_mode="$3"
  local threshold="$4"
  local note="$5"
  sample_names+=("${name}")
  sample_artifacts+=("${artifact}")
  sample_trace_modes+=("${trace_mode}")
  sample_thresholds+=("${threshold}")
  sample_notes+=("${note}")
}

jar_plugin_with_main() {
  local main_class="$1"
  local add_classpath="${2:-false}"
  local classpath_xml=""
  if [[ "${add_classpath}" == "true" ]]; then
    classpath_xml="
                    <addClasspath>true</addClasspath>
                    <classpathPrefix>lib/</classpathPrefix>"
  fi
  cat <<XML
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-jar-plugin</artifactId>
                <version>3.4.2</version>
                <configuration>
                    <archive>
                        <manifest>
                            <mainClass>${main_class}</mainClass>${classpath_xml}
                        </manifest>
                    </archive>
                </configuration>
            </plugin>
XML
}

copy_dependencies_plugin() {
  cat <<'XML'
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-dependency-plugin</artifactId>
                <version>3.7.1</version>
                <executions>
                    <execution>
                        <id>copy-dependencies</id>
                        <phase>package</phase>
                        <goals>
                            <goal>copy-dependencies</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${project.build.directory}/lib</outputDirectory>
                            <includeScope>runtime</includeScope>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
XML
}

create_plain_maven_jar() {
  local dir="${SOURCES_DIR}/plain-maven-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/plain" "${dir}/src/main/resources"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>plain-maven-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <build>
        <plugins>
$(jar_plugin_with_main "com.example.plain.PlainMain")
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/plain-message.txt" <<'TXT'
plain-resource
TXT
  write_file "${dir}/src/main/java/com/example/plain/PlainMain.java" <<'JAVA'
package com.example.plain;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class PlainMain {
    public static void main(String[] args) throws Exception {
        Class<?> target = Class.forName("com.example.plain.PlainTarget");
        Object value = target.getMethod("message").invoke(target.newInstance());
        try (InputStream resource = PlainMain.class.getResourceAsStream("/plain-message.txt")) {
            if (resource == null) {
                throw new IllegalStateException("missing resource");
            }
            byte[] content = readAll(resource);
            Path temp = Files.createTempFile("jar2mp-plain", ".txt");
            Files.copy(new ByteArrayInputStream(content), temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            try (InputStream file = Files.newInputStream(temp)) {
                System.out.println(value + ":" + new String(readAll(file), StandardCharsets.UTF_8).trim());
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/plain/PlainTarget.java" <<'JAVA'
package com.example.plain;

public class PlainTarget {
    public String message() {
        return "plain-target";
    }
}
JAVA
  run_maven "${dir}"
  register_sample "plain-maven-jar" "${dir}/target/plain-maven-jar-1.0.0.jar" "trace" "95" "Executable Maven JAR with reflection, resource, and file I/O."
}

create_spring_boot_jar() {
  local dir="${SOURCES_DIR}/spring-boot-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/boot" "${dir}/src/main/resources/templates" "${dir}/src/main/resources/static"
  write_file "${dir}/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>2.7.18</version>
        <relativePath/>
    </parent>
    <groupId>com.example.regression</groupId>
    <artifactId>spring-boot-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <java.version>1.8</java.version>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/application.yml" <<'YAML'
spring:
  main:
    web-application-type: none
YAML
  write_file "${dir}/src/main/resources/templates/hello.txt" <<'TXT'
template-loaded
TXT
  write_file "${dir}/src/main/resources/static/app.js" <<'JS'
window.jar2mpRegression = true;
JS
  write_file "${dir}/src/main/java/com/example/boot/BootMain.java" <<'JAVA'
package com.example.boot;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BootMain implements CommandLineRunner {
    private final GreetingService greetingService;

    public BootMain(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    public static void main(String[] args) {
        SpringApplication.run(BootMain.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Class<?> target = Class.forName("com.example.boot.ReflectionTarget");
        Object reflected = target.getMethod("message").invoke(target.newInstance());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getClass().getResourceAsStream("/templates/hello.txt"), StandardCharsets.UTF_8))) {
            String template = reader.lines().collect(Collectors.joining("\n"));
            System.out.println(reflected + " | " + greetingService.greet("jar2mp") + " | " + template);
        }
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/boot/GreetingService.java" <<'JAVA'
package com.example.boot;

public interface GreetingService {
    String greet(String name);
}
JAVA
  write_file "${dir}/src/main/java/com/example/boot/DefaultGreetingService.java" <<'JAVA'
package com.example.boot;

import org.springframework.stereotype.Service;

@Service
public class DefaultGreetingService implements GreetingService {
    @Override
    public String greet(String name) {
        return "hello " + name;
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/boot/ReflectionTarget.java" <<'JAVA'
package com.example.boot;

public class ReflectionTarget {
    public String message() {
        return "reflected-message";
    }
}
JAVA
  run_maven "${dir}"
  register_sample "spring-boot-jar" "${dir}/target/spring-boot-jar-1.0.0.jar" "trace" "95" "Spring Boot executable JAR with BOOT-INF/classes and nested libraries."
}

create_war() {
  local dir="${SOURCES_DIR}/servlet-war"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/war" "${dir}/src/main/webapp/WEB-INF" "${dir}/src/main/resources"
  write_file "${dir}/pom.xml" <<'XML'
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>servlet-war</artifactId>
    <version>1.0.0</version>
    <packaging>war</packaging>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>javax.servlet</groupId>
            <artifactId>javax.servlet-api</artifactId>
            <version>4.0.1</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-war-plugin</artifactId>
                <version>3.4.0</version>
            </plugin>
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/war.properties" <<'TXT'
name=servlet-war
TXT
  write_file "${dir}/src/main/webapp/index.html" <<'HTML'
<html><body>servlet-war</body></html>
HTML
  write_file "${dir}/src/main/webapp/WEB-INF/web.xml" <<'XML'
<web-app xmlns="http://xmlns.jcp.org/xml/ns/javaee" version="4.0">
    <servlet>
        <servlet-name>sample</servlet-name>
        <servlet-class>com.example.war.SampleServlet</servlet-class>
    </servlet>
    <servlet-mapping>
        <servlet-name>sample</servlet-name>
        <url-pattern>/sample</url-pattern>
    </servlet-mapping>
</web-app>
XML
  write_file "${dir}/src/main/java/com/example/war/SampleServlet.java" <<'JAVA'
package com.example.war;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class SampleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.getWriter().write("servlet-war:" + request.getRequestURI());
    }
}
JAVA
  run_maven "${dir}"
  register_sample "servlet-war" "${dir}/target/servlet-war-1.0.0.war" "verify-only" "75" "Servlet WAR; no standalone runtime trace is expected."
}

create_mybatis_jar() {
  local dir="${SOURCES_DIR}/mybatis-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/mybatis" "${dir}/src/main/resources/mapper"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>mybatis-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.mybatis</groupId>
            <artifactId>mybatis</artifactId>
            <version>3.5.16</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
$(jar_plugin_with_main "com.example.mybatis.MybatisMain" "true")
$(copy_dependencies_plugin)
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/mybatis-config.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE configuration
  PUBLIC "-//mybatis.org//DTD Config 3.0//EN"
  "https://mybatis.org/dtd/mybatis-3-config.dtd">
<configuration>
    <mappers>
        <mapper resource="mapper/UserMapper.xml"/>
    </mappers>
</configuration>
XML
  write_file "${dir}/src/main/resources/mapper/UserMapper.xml" <<'XML'
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
  PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
  "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.example.mybatis.UserMapper">
    <select id="findName" resultType="string">
        select 'jar2mp'
    </select>
</mapper>
XML
  write_file "${dir}/src/main/java/com/example/mybatis/MybatisMain.java" <<'JAVA'
package com.example.mybatis;

import java.io.InputStream;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class MybatisMain {
    public static void main(String[] args) throws Exception {
        try (InputStream input = Resources.getResourceAsStream("mybatis-config.xml")) {
            SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(input);
            System.out.println(factory.getConfiguration().getMappedStatementNames().size());
        }
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/mybatis/UserMapper.java" <<'JAVA'
package com.example.mybatis;

public interface UserMapper {
    String findName();
}
JAVA
  run_maven "${dir}"
  register_sample "mybatis-jar" "${dir}/target/mybatis-jar-1.0.0.jar" "trace" "90" "Thin Maven JAR with manifest Class-Path and MyBatis XML resources."
}

create_shiro_jar() {
  local dir="${SOURCES_DIR}/shiro-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/shiro" "${dir}/src/main/resources"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>shiro-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.apache.shiro</groupId>
            <artifactId>shiro-core</artifactId>
            <version>1.13.0</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
$(jar_plugin_with_main "com.example.shiro.ShiroMain" "true")
$(copy_dependencies_plugin)
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/shiro.ini" <<'TXT'
[users]
alice = secret, admin
TXT
  write_file "${dir}/src/main/java/com/example/shiro/ShiroMain.java" <<'JAVA'
package com.example.shiro;

import java.io.InputStream;
import org.apache.shiro.subject.SimplePrincipalCollection;

public class ShiroMain {
    public static void main(String[] args) throws Exception {
        SimplePrincipalCollection principals = new SimplePrincipalCollection("alice", "memoryRealm");
        try (InputStream input = ShiroMain.class.getResourceAsStream("/shiro.ini")) {
            if (input == null) {
                throw new IllegalStateException("missing shiro.ini");
            }
            System.out.println(principals.getPrimaryPrincipal() + ":" + input.available());
        }
    }
}
JAVA
  run_maven "${dir}"
  register_sample "shiro-jar" "${dir}/target/shiro-jar-1.0.0.jar" "trace" "90" "Thin Maven JAR using Apache Shiro with manifest Class-Path."
}

create_spring_security_jar() {
  local dir="${SOURCES_DIR}/spring-security-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/springsecurity" "${dir}/src/main/resources"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>spring-security-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <dependencies>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-core</artifactId>
            <version>5.8.13</version>
        </dependency>
    </dependencies>
    <build>
        <plugins>
$(jar_plugin_with_main "com.example.springsecurity.SpringSecurityMain" "true")
$(copy_dependencies_plugin)
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/resources/security.properties" <<'TXT'
mode=regression
TXT
  write_file "${dir}/src/main/java/com/example/springsecurity/SpringSecurityMain.java" <<'JAVA'
package com.example.springsecurity;

import java.io.InputStream;
import java.util.Collections;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

public class SpringSecurityMain {
    public static void main(String[] args) throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("alice", "secret", Collections.emptyList());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try (InputStream input = SpringSecurityMain.class.getResourceAsStream("/security.properties")) {
            if (input == null) {
                throw new IllegalStateException("missing security.properties");
            }
            System.out.println(context.getAuthentication().getName() + ":" + input.available());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
JAVA
  run_maven "${dir}"
  register_sample "spring-security-jar" "${dir}/target/spring-security-jar-1.0.0.jar" "trace" "90" "Thin Maven JAR using Spring Security with manifest Class-Path."
}

create_obfuscated_jar() {
  local dir="${SOURCES_DIR}/obfuscated-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/obfuscated" "${dir}/src/main/resources"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>obfuscated-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <profiles>
        <profile>
            <id>jdk9-plus</id>
            <activation>
                <jdk>[9,)</jdk>
            </activation>
            <properties>
                <proguard.library>\${java.home}/jmods/java.base.jmod</proguard.library>
            </properties>
        </profile>
        <profile>
            <id>jdk8</id>
            <activation>
                <jdk>[1.8,9)</jdk>
            </activation>
            <properties>
                <proguard.library>\${java.home}/lib/rt.jar</proguard.library>
            </properties>
        </profile>
    </profiles>
    <build>
        <plugins>
$(jar_plugin_with_main "com.example.obfuscated.ObfuscatedMain")
            <plugin>
                <groupId>com.github.wvengen</groupId>
                <artifactId>proguard-maven-plugin</artifactId>
                <version>2.7.0</version>
                <executions>
                    <execution>
                        <id>obfuscate</id>
                        <phase>package</phase>
                        <goals>
                            <goal>proguard</goal>
                        </goals>
                    </execution>
                </executions>
                <dependencies>
                    <dependency>
                        <groupId>com.guardsquare</groupId>
                        <artifactId>proguard-base</artifactId>
                        <version>7.7.0</version>
                    </dependency>
                    <dependency>
                        <groupId>com.guardsquare</groupId>
                        <artifactId>proguard-core</artifactId>
                        <version>9.1.10</version>
                    </dependency>
                </dependencies>
                <configuration>
                    <obfuscate>true</obfuscate>
                    <injar>\${project.build.finalName}.jar</injar>
                    <outjar>\${project.build.finalName}-obfuscated.jar</outjar>
                    <proguardInclude>\${project.basedir}/proguard.pro</proguardInclude>
                    <libs>
                        <lib>\${proguard.library}</lib>
                    </libs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/proguard.pro" <<'TXT'
-dontshrink
-dontoptimize
-dontwarn
-keepattributes SourceFile,LineNumberTable
-keep public class com.example.obfuscated.ObfuscatedMain {
    public static void main(java.lang.String[]);
}
TXT
  write_file "${dir}/src/main/resources/obfuscated.txt" <<'TXT'
payload=obfuscated
TXT
  write_file "${dir}/src/main/java/com/example/obfuscated/ObfuscatedMain.java" <<'JAVA'
package com.example.obfuscated;

import java.io.InputStream;

public class ObfuscatedMain {
    public static void main(String[] args) throws Exception {
        InputStream input = ObfuscatedMain.class.getResourceAsStream("/obfuscated.txt");
        if (input == null) {
            throw new IllegalStateException("missing obfuscated.txt");
        }
        Flow flow = new Flow();
        System.out.println(flow.message(args.length) + ":" + input.getClass().getName());
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/obfuscated/Flow.java" <<'JAVA'
package com.example.obfuscated;

class Flow {
    String message(int seed) {
        int value = seed;
        for (int i = 0; i < 7; i++) {
            value ^= (i * 31) + (seed << 2);
        }
        return Integer.toHexString((value << 1) ^ 0x5a5a);
    }
}
JAVA
  run_maven "${dir}"
  register_sample "obfuscated-jar" "${dir}/target/obfuscated-jar-1.0.0-obfuscated.jar" "trace" "85" "ProGuard-obfuscated JAR with renamed implementation classes."
}

create_no_debug_jar() {
  local dir="${SOURCES_DIR}/no-debug-jar"
  rm -rf "${dir}"
  mkdir -p "${dir}/src/main/java/com/example/nodebug"
  write_file "${dir}/pom.xml" <<XML
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.example.regression</groupId>
    <artifactId>no-debug-jar</artifactId>
    <version>1.0.0</version>
    <properties>
        <maven.compiler.source>8</maven.compiler.source>
        <maven.compiler.target>8</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <debug>false</debug>
                </configuration>
            </plugin>
$(jar_plugin_with_main "com.example.nodebug.NoDebugMain")
        </plugins>
    </build>
</project>
XML
  write_file "${dir}/src/main/java/com/example/nodebug/NoDebugMain.java" <<'JAVA'
package com.example.nodebug;

public class NoDebugMain {
    public static void main(String[] args) throws Exception {
        Class<?> target = Class.forName("com.example.nodebug.NoDebugTarget");
        Object value = target.getDeclaredConstructor().newInstance();
        System.out.println(target.getMethod("message").invoke(value));
    }
}
JAVA
  write_file "${dir}/src/main/java/com/example/nodebug/NoDebugTarget.java" <<'JAVA'
package com.example.nodebug;

public class NoDebugTarget {
    public String message() {
        String value = "no-debug";
        return value.toUpperCase(java.util.Locale.ROOT);
    }
}
JAVA
  run_maven "${dir}"
  register_sample "no-debug-jar" "${dir}/target/no-debug-jar-1.0.0.jar" "trace" "85" "JAR compiled with debug=false to exercise missing LocalVariableTable handling."
}

parse_overall_score() {
  local report="$1"
  awk -F'[:/]' '/^- Overall:/ {gsub(/ /, "", $2); print $2; exit}' "${report}"
}

parse_bucket_score() {
  local report="$1"
  local bucket="$2"
  awk -v bucket="${bucket}" -F'|' '$2 ~ " " bucket " " {gsub(/ /, "", $3); print $3; exit}' "${report}"
}

parse_verification_status() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'not-run'
    return
  fi
  awk -F': ' '/^- Summary:/ {print $2; exit}' "${report}"
}

parse_verification_failure_type() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'not-run'
    return
  fi
  awk -F': ' '/^- Failure type:/ {print $2; exit}' "${report}"
}

parse_runtime_exit() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'not-run'
    return
  fi
  awk -F': ' '/^- Exit code:/ {print $2; exit}' "${report}"
}

parse_runtime_events() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'not-run'
    return
  fi
  awk -F': ' '/^- Total events:/ {print $2; exit}' "${report}"
}

parse_decompile_failures() {
  local report="$1"
  if [[ ! -f "${report}" ]]; then
    printf 'missing-report'
    return
  fi
  if grep -q 'No decompilation failures detected.' "${report}"; then
    printf '0'
  else
    grep -c '^- Failed to decompile ' "${report}" || true
  fi
}

run_sample() {
  local index="$1"
  local name="${sample_names[${index}]}"
  local artifact="${sample_artifacts[${index}]}"
  local trace_mode="${sample_trace_modes[${index}]}"
  local threshold="${sample_thresholds[${index}]}"
  local note="${sample_notes[${index}]}"
  local output_base="${RESTORE_DIR}/${name}"

  rm -rf "${output_base}"
  mkdir -p "${output_base}"

  local args=(--verbose --verify-build --verify-goal compile -f -o "${output_base}")
  if [[ "${trace_mode}" == "trace" ]]; then
    args=(--verbose --trace-runtime --trace-timeout "${JAVA_TRACE_TIMEOUT}" --verify-build --verify-goal compile -f -o "${output_base}")
  fi
  args+=("${artifact}")

  log "Restoring ${name}"
  set +e
  java -jar "${JAR2MP_JAR}" "${args[@]}" > "${REPORT_DIR}/${name}.cli.log" 2>&1
  local exit_code=$?
  set -e

  local project_dir
  project_dir="$(find "${output_base}" -mindepth 1 -maxdepth 1 -type d | sort | head -n 1 || true)"
  local score_report="${project_dir}/restoration-score.md"
  local verification_report="${project_dir}/verification-report.md"
  local runtime_report="${project_dir}/runtime-trace-report.md"
  local failures_report="${project_dir}/decompile-failures.md"

  local overall="0"
  local source_score="0"
  local resource_score="0"
  local runtime_score="0"
  local verification_score="0"
  local verification_status="not-run"
  local verification_failure_type="not-run"
  local runtime_exit="not-run"
  local runtime_events="not-run"
  local decompile_failures="missing"
  local status="FAIL"

  if [[ -n "${project_dir}" && -f "${score_report}" ]]; then
    overall="$(parse_overall_score "${score_report}")"
    source_score="$(parse_bucket_score "${score_report}" "source")"
    resource_score="$(parse_bucket_score "${score_report}" "resource")"
    runtime_score="$(parse_bucket_score "${score_report}" "runtime")"
    verification_score="$(parse_bucket_score "${score_report}" "verification")"
    verification_status="$(parse_verification_status "${verification_report}")"
    verification_failure_type="$(parse_verification_failure_type "${verification_report}")"
    runtime_exit="$(parse_runtime_exit "${runtime_report}")"
    runtime_events="$(parse_runtime_events "${runtime_report}")"
    decompile_failures="$(parse_decompile_failures "${failures_report}")"
    overall="${overall:-0}"
    source_score="${source_score:-0}"
    resource_score="${resource_score:-0}"
    runtime_score="${runtime_score:-0}"
    verification_score="${verification_score:-0}"
    verification_status="${verification_status:-not-run}"
    verification_failure_type="${verification_failure_type:-not-run}"
    runtime_exit="${runtime_exit:-not-run}"
    runtime_events="${runtime_events:-not-run}"
    decompile_failures="${decompile_failures:-missing}"
    local trace_ok="true"
    if [[ "${trace_mode}" == "trace" ]]; then
      trace_ok="false"
      if [[ "${runtime_exit}" == "0" && "${runtime_score}" -ge 100 ]] && is_positive_integer "${runtime_events}"; then
        trace_ok="true"
      fi
    fi
    if [[ "${exit_code}" -eq 0 \
      && "${overall}" -ge "${threshold}" \
      && "${verification_status}" == "BUILD SUCCESS" \
      && "${verification_failure_type}" == "NONE" \
      && "${source_score}" -eq 100 \
      && "${resource_score}" -eq 100 \
      && "${decompile_failures}" == "0" \
      && "${trace_ok}" == "true" ]]; then
      status="PASS"
    fi
  fi

  {
    csv_field "${name}"; printf ','
    csv_field "${status}"; printf ','
    csv_field "${trace_mode}"; printf ','
    csv_field "${overall}"; printf ','
    csv_field "${source_score}"; printf ','
    csv_field "${resource_score}"; printf ','
    csv_field "${runtime_score}"; printf ','
    csv_field "${verification_score}"; printf ','
    csv_field "${verification_status}"; printf ','
    csv_field "${verification_failure_type}"; printf ','
    csv_field "${runtime_exit}"; printf ','
    csv_field "${runtime_events}"; printf ','
    csv_field "${decompile_failures}"; printf ','
    csv_field "${threshold}"; printf ','
    csv_field "${note}"; printf '\n'
  } >> "${REPORT_DIR}/regression-summary.csv"

  cat >> "${REPORT_DIR}/regression-summary.md" <<MD
| ${name} | ${status} | ${trace_mode} | ${overall} | ${source_score} | ${resource_score} | ${runtime_score} | ${verification_score} | ${verification_status} | ${verification_failure_type} | ${runtime_exit} | ${runtime_events} | ${decompile_failures} | ${threshold} |
MD
}

main() {
  rm -rf "${WORK_DIR}"
  mkdir -p "${SOURCES_DIR}" "${RESTORE_DIR}" "${REPORT_DIR}"

  log "Building jar2mp"
  (cd "${ROOT_DIR}" && "${MVN}" -q -DskipTests package)

  log "Generating sample projects"
  create_plain_maven_jar
  create_spring_boot_jar
  create_war
  create_mybatis_jar
  create_shiro_jar
  create_spring_security_jar
  create_obfuscated_jar
  create_no_debug_jar

  write_file "${REPORT_DIR}/regression-summary.csv" <<'CSV'
sample,status,trace_mode,overall,source,resource,runtime,verification,verification_status,verification_failure_type,runtime_exit,runtime_events,decompile_failures,threshold,note
CSV
  write_file "${REPORT_DIR}/regression-summary.md" <<'MD'
# jar2mp Sample Regression Summary

| Sample | Status | Trace mode | Overall | Source | Resource | Runtime | Verification | Verification status | Failure type | Runtime exit | Runtime events | Decompile failures | Threshold |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- | --- | --- | ---: | ---: | ---: |
MD

  local i
  for i in "${!sample_names[@]}"; do
    run_sample "${i}"
  done

  log "Summary: ${REPORT_DIR}/regression-summary.md"
  log "CSV: ${REPORT_DIR}/regression-summary.csv"
  if grep -q ',"FAIL",' "${REPORT_DIR}/regression-summary.csv"; then
    log "At least one sample failed."
    exit 1
  fi
}

main "$@"
