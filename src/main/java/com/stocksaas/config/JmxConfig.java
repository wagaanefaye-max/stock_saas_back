package com.stocksaas.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jmx.JmxAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jmx.export.MBeanExporter;

/**
 * Configuration pour désactiver complètement JMX et éviter les erreurs RMI
 */
@Configuration
@AutoConfigureAfter(JmxAutoConfiguration.class)
public class JmxConfig {
    
    /**
     * Désactive complètement l'exportation MBean
     * Cette configuration empêche les erreurs InstanceNotFoundException
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "spring.jmx.enabled", havingValue = "false", matchIfMissing = true)
    public MBeanExporter mbeanExporter() {
        MBeanExporter exporter = new MBeanExporter();
        // Exclure tous les beans de l'exportation JMX
        exporter.setExcludedBeans("*");
        exporter.setAutodetect(false);
        exporter.setAssembler(null);
        // Ne pas utiliser le MBeanServer par défaut
        exporter.setServer(null);
        return exporter;
    }
}
