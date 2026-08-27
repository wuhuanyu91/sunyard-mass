package com.sunyard.llm.mas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;

/**
 * 启动时自动建表并插入种子数据（附录 E.7 零配置可跑）。
 * DB 不可达时不阻断启动：各 DB 依赖环节均有降级策略（L2 跳过、配额 fail-open）。
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public ApplicationRunner masSchemaInitializer(DataSource dataSource) {
        return args -> {
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                        new ClassPathResource("db/schema.sql"),
                        new ClassPathResource("db/data.sql"));
                populator.setContinueOnError(true);
                populator.execute(dataSource);
                log.info("MAS schema/seed initialized successfully");
            } catch (Exception e) {
                log.warn("Schema/seed init skipped (DB unavailable): {}", e.getMessage());
            }
        };
    }
}
