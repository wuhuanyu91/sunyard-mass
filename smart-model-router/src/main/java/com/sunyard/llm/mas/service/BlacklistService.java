package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.mapper.BlacklistMapper;
import com.sunyard.llm.mas.util.ReactiveDbAdapter;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.Set;

/**
 * L1 黑名单：配置列表 + PG mas_blacklist 表取并集（附录 G.7）。
 * 启动加载，30 秒刷新；DB 不可用时仅用配置列表（fail-open 降级）。
 */
@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    private final MasProperties props;
    private final BlacklistMapper mapper;
    private volatile Set<String> dbUsers = Set.of();

    public BlacklistService(MasProperties props, BlacklistMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        refresh().subscribe(null, e -> log.warn("Blacklist initial load failed: {}", e.getMessage()));
    }

    @Scheduled(fixedDelay = 30_000)
    public void scheduledRefresh() {
        refresh().subscribe(null, e -> log.warn("Blacklist refresh failed: {}", e.getMessage()));
    }

    public Mono<Void> refresh() {
        return ReactiveDbAdapter.mono(mapper::selectActiveSubjectKeys)
                .map(list -> new HashSet<>(list))
                .doOnNext(set -> this.dbUsers = set)
                .then();
    }

    public boolean isBlacklisted(String userId) {
        if (userId == null) {
            return false;
        }
        return props.getBlacklist().getUsers().contains(userId) || dbUsers.contains(userId);
    }
}
