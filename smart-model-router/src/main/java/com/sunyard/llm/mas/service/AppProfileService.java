package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.entity.AppEntity;
import com.sunyard.llm.mas.mapper.AppMapper;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 应用画像缓存：app_id → 部门 / SLA 等级 / 数据等级。
 * <p>
 * 【改造背景】mas_call_log 的 dept_id / sla_level / data_level 此前恒写 null
 * （注释写着"由计量侧解析""后续补齐"），导致数据分级与成本归属在库里根本落不下去。
 * 60s 缓存避免每次调用都查库。
 */
@Service
public class AppProfileService {

    private static final long CACHE_TTL_MS = 60_000L;

    private final AppMapper appMapper;

    private static final class Profile {
        final String deptId;
        final String slaLevel;
        final String dataLevel;
        final long expireAt;

        Profile(String deptId, String slaLevel, String dataLevel) {
            this.deptId = deptId;
            this.slaLevel = slaLevel;
            this.dataLevel = dataLevel;
            this.expireAt = System.currentTimeMillis() + CACHE_TTL_MS;
        }
    }

    private final ConcurrentHashMap<String, Profile> cache = new ConcurrentHashMap<>();

    public AppProfileService(AppMapper appMapper) {
        this.appMapper = appMapper;
    }

    /** 解析应用画像；未注册应用返回默认画像（P1 / L2），保证链路不中断 */
    public Profile resolve(String appId) {
        if (appId == null || appId.isBlank()) return defaultProfile();
        Profile cached = cache.get(appId);
        if (cached != null && cached.expireAt > System.currentTimeMillis()) {
            return cached;
        }
        Profile loaded = load(appId);
        cache.put(appId, loaded);
        return loaded;
    }

    public String deptOf(String appId) {
        return resolve(appId).deptId;
    }

    public String slaOf(String appId) {
        return resolve(appId).slaLevel;
    }

    public String dataLevelOf(String appId) {
        return resolve(appId).dataLevel;
    }

    /** 应用配置变更后由 AppService 调用失效缓存 */
    public void evict(String appId) {
        if (appId != null) cache.remove(appId);
    }

    private Profile load(String appId) {
        try {
            AppEntity e = appMapper == null ? null : appMapper.selectByAppId(appId);
            if (e == null) return defaultProfile();
            return new Profile(
                    blankToDefault(e.getDeptId(), "UNKNOWN"),
                    blankToDefault(e.getSlaLevel(), "P1"),
                    blankToDefault(e.getDataLevel(), "L2"));
        } catch (Exception ex) {
            return defaultProfile();
        }
    }

    private static Profile defaultProfile() {
        return new Profile("UNKNOWN", "P1", "L2");
    }

    private static String blankToDefault(String v, String def) {
        return v == null || v.isBlank() ? def : v;
    }

    /** 供外部以 Map 形式读取，便于管理面展示 */
    public Map<String, Object> toMap(String appId) {
        Profile p = resolve(appId);
        return Map.of("deptId", p.deptId, "slaLevel", p.slaLevel, "dataLevel", p.dataLevel);
    }
}
