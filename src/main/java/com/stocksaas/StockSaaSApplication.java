package com.stocksaas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.admin.SpringApplicationAdminJmxAutoConfiguration;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
    JmxAutoConfiguration.class,
    SpringApplicationAdminJmxAutoConfiguration.class
})
@ConfigurationPropertiesScan
@EnableScheduling
public class StockSaaSApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(StockSaaSApplication.class);
    }

    public static void main(String[] args) {
        // Désactiver JMX pour éviter InstanceNotFoundException (connexions IDE/monitoring)
        System.setProperty("spring.jmx.enabled", "false");
        System.setProperty("com.sun.management.jmxremote", "false");
        System.setProperty("com.sun.management.jmxremote.port", "-1");

        SpringApplication app = new SpringApplication(StockSaaSApplication.class);
        app.setRegisterShutdownHook(true);
        app.run(args);
    }
}
