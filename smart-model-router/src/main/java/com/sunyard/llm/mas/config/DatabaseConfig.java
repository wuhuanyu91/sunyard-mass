package com.sunyard.llm.mas.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;
import io.r2dbc.spi.ConnectionFactory;

/**
 * 启动时自动建表并插入种子数据（附录 E.7 零配置可跑）。
 * DB 不可达时不阻断启动：各 DB 依赖环节均有降级策略（L2 跳过、配额 fail-open）。
 */
@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    public ApplicationRunner masSchemaInitializer(ConnectionFactory connectionFactory) {
        return args -> {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    new ClassPathResource("db/schema.sql"),
                    new ClassPathResource("db/data.sql"));
            populator.setContinueOnError(true);
            populator.populate(connectionFactory)
                    .subscribe(null, e -> log.warn(
                            "Schema/seed init skipped (DB unavailable): {}", e.getMessage()));
        };
    }
}
