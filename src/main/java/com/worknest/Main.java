package com.worknest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableCaching
public class Main {
    public static void main(String[] args) {
        preventAmbiguousDebugEnvironmentFromEnablingSpringDebug();
        SpringApplication.run(Main.class, args);
    }

    private static void preventAmbiguousDebugEnvironmentFromEnablingSpringDebug() {
        String environmentDebug = System.getenv("DEBUG");
        if (System.getProperty("debug") == null
                && environmentDebug != null
                && !environmentDebug.equalsIgnoreCase("true")
                && !environmentDebug.equalsIgnoreCase("false")) {
            /*
             * DEBUG is also commonly used by Node-based tooling for namespace
             * selectors (for example "release"). Spring Boot treats its mere
             * presence as a debug switch, so neutralize non-boolean values.
             * An explicit --debug=true command-line argument still wins.
             */
            System.setProperty("debug", "false");
        }
    }
}
