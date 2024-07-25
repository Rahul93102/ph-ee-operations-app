package org.apache.fineract.core.tenants;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.apache.fineract.core.service.DataSourcePerTenantService;
import org.apache.fineract.core.service.migrate.TenantDatabaseUpgradeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class TenantsService implements DisposableBean {
    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private Environment environment;

    @Autowired
    TenantConnections tenantConnectionList;

    @Autowired
    DataSourcePerTenantService dataSourcePerTenantService;

    @Autowired
    private TenantDatabaseUpgradeService tenantDatabaseUpgradeService;

    @Autowired
    private ApplicationContext springContext;

    private Map<String, TenantConnectionProperties> tenantConnectionProperties;

    private Map<String, DataSource> tenantDataSources = new HashMap<>();


    @PostConstruct
    public void setup() {
        String[] activeProfiles = environment.getActiveProfiles();
        List<String> tenants = Stream.of(activeProfiles)
                .map(profile -> profile.startsWith("tenant-") ? profile.substring("tenant-".length()) : null)
                .filter(Objects::nonNull)
                .toList();
        logger.info("Loaded tenants from configuration: {}", tenants);

        this.tenantConnectionProperties = tenantConnectionList.getConnections().stream()
                .collect(Collectors.toMap(TenantConnectionProperties::getName, it -> it));
        logger.info("loaded {} tenant config properties: {}", tenantConnectionProperties.size(), tenantConnectionProperties);

        tenantConnectionProperties.forEach((name, properties) -> {
            logger.info("Creating datasource for tenant {}", name);
            tenantDataSources.put(name, dataSourcePerTenantService.createNewDataSourceFor(properties));
        });

        if (List.of(activeProfiles).contains("migrate")) {
            logger.info("Running in migration mode, migrating tenants: {}", tenantDataSources.keySet());
            tenantDatabaseUpgradeService.migrateTenants(tenantDataSources, tenantConnectionProperties);
            logger.info("Migration finished, exiting");
            new Thread(() -> SpringApplication.exit(springContext, () -> 0)).start();
        }
    }

    public DataSource getTenantDataSource(String tenantIdentifier) {
        return tenantDataSources.get(tenantIdentifier);
    }

    // for initializing JPA repositories
    public DataSource getAnyDataSource() {
        return tenantDataSources.values().iterator().next();
    }

    @Override
    public void destroy() {
        logger.info("Closing {} datasources", tenantDataSources.size());
        this.tenantDataSources.forEach((name, ds) -> {
            try {
                ((HikariDataSource) ds).close();
            } catch (Exception e) {
                logger.error("Error closing datasource for tenant {}", name, e);
            }
        });
    }
}