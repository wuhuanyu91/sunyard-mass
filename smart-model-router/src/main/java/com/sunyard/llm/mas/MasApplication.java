package com.sunyard.llm.mas;

import com.sunyard.llm.mas.config.MasProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MAS 智能模型路由访问平台原型启动类。
 * 四层流水线（L1 规则拦截 / L2 多级缓存 / L3 意图路由 / L4 执行管控）见设计方案 §5 与附录 G。
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(MasProperties.class)
public class MasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MasApplication.class, args);
    }
}
