package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据分级差异化管控的执行点（招标二-5 数据保护）：
 * 把「应用的数据等级」与「模型的部署形态」做合规匹配，命中不合规时先尝试在同意图内
 * 换一个合规模型（降级不失控），都不可用时才按策略拒绝。
 * <p>
 * 【改造背景】data_level / sla_level 此前只有字段、无执行逻辑，
 * L3 路由完全不看数据等级，L3 敏感数据同样可能被路由到云端或租赁算力上。
 */
@Service
public class DataLevelGuardService {

    private final DataLevelPolicyService policyService;
    private final AppProfileService appProfileService;
    private final ModelRouter modelRouter;
    private final SecurityEventService securityEventService;
    private final MasProperties props;

    public DataLevelGuardService(DataLevelPolicyService policyService, AppProfileService appProfileService,
                                 ModelRouter modelRouter, SecurityEventService securityEventService,
                                 MasProperties props) {
        this.policyService = policyService;
        this.appProfileService = appProfileService;
        this.modelRouter = modelRouter;
        this.securityEventService = securityEventService;
        this.props = props;
    }

    public boolean enabled() {
        return props == null || props.getGovernance() == null || props.getGovernance().isDataLevelGuardEnabled();
    }

    /** 当前应用的数据等级（L1/L2/L3） */
    public String dataLevelOf(String appId) {
        return appProfileService == null ? "L2" : appProfileService.dataLevelOf(appId);
    }

    /** 该数据等级是否允许由指定部署形态承载 */
    public boolean allow(String dataLevel, String deployType) {
        if (!enabled()) return true;
        return policyService == null || policyService.allowDeployType(dataLevel, deployType);
    }

    /**
     * 合规校验并必要时改选模型：
     * 目标模型不合规时，在同意图/默认模型里找一个合规替代；找不到则抛 403。
     *
     * @return 最终可用的模型配置（可能已被改选）
     */
    public ModelConfig guard(String appId, ModelConfig target, String intent) {
        if (!enabled() || target == null) return target;
        String dataLevel = dataLevelOf(appId);
        String deployType = target.deployTypeOrDefault();
        if (allow(dataLevel, deployType)) {
            return target;
        }
        ModelConfig replacement = findCompliant(intent, dataLevel);
        if (replacement != null) {
            return replacement;
        }
        if (securityEventService != null) {
            securityEventService.recordPipelineEvent(null, appId, null, target.modelId(), "L3",
                    "DATA_LEVEL_DENIED", "HIGH", "data_level_denied",
                    "模型 " + target.modelId() + "（" + deployType + "）不允许承载 " + dataLevel + " 级数据，且无合规替代模型");
        }
        throw MasException.dataLevelDenied(target.modelId(), deployType, dataLevel);
    }

    /** 在同意图候选中寻找一个合规模型，其次退到全局默认模型 */
    private ModelConfig findCompliant(String intent, String dataLevel) {
        if (modelRouter == null) return null;
        if (intent != null && !intent.isBlank()) {
            List<ModelConfig> candidates = modelRouter.activeModels().stream()
                    .filter(c -> c.intentType() != null && c.intentType().equalsIgnoreCase(intent))
                    .filter(c -> allow(dataLevel, c.deployTypeOrDefault()))
                    .toList();
            ModelConfig picked = modelRouter.weightedPick(candidates);
            if (picked != null) return picked;
        }
        ModelConfig any = modelRouter.activeModels().stream()
                .filter(c -> allow(dataLevel, c.deployTypeOrDefault()))
                .findFirst().orElse(null);
        if (any != null) return any;
        ModelConfig def = modelRouter.defaultModel();
        return allow(dataLevel, def.deployTypeOrDefault()) ? def : null;
    }

    /** 该等级的上下文长度上限（0 = 不限制），用于 L4 压缩前的二次收敛 */
    public int contextCeiling(String dataLevel) {
        if (!enabled()) return 0;
        return policyService == null ? 0 : policyService.maxContextTokens(dataLevel);
    }
}
