package com.sunyard.llm.mas.pipeline.stage;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.exception.MasException;
import com.sunyard.llm.mas.pipeline.PipelineContext;
import com.sunyard.llm.mas.pipeline.PipelineContextFactory;
import com.sunyard.llm.mas.service.ApiKeyService;
import com.sunyard.llm.mas.service.BlacklistService;
import com.sunyard.llm.mas.service.RateLimitService;
import com.sunyard.llm.mas.service.SensitiveWordFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L1 鉴权开关与身份链测试：
 * mas.auth.enabled=false 跳过 Key 校验（零改造接入），身份回退 body.user → X-User-Id → anonymous；
 * 开启时身份以 API Key 绑定值为准，body.user 仅作 agent_id 审计保留。
 */
class L1RuleInterceptStageTest {

    private BlacklistService blacklist;
    private SensitiveWordFilter sensitive;
    private RateLimitService rateLimit;
    private ApiKeyService apiKey;
    private MasProperties props;
    private L1RuleInterceptStage stage;

    @BeforeEach
    void setUp() {
        blacklist = mock(BlacklistService.class);
        sensitive = mock(SensitiveWordFilter.class);
        rateLimit = mock(RateLimitService.class);
        apiKey = mock(ApiKeyService.class);
        props = new MasProperties();
        stage = new L1RuleInterceptStage(blacklist, sensitive, rateLimit, apiKey, props);

        when(blacklist.isBlacklisted(anyString())).thenReturn(false);
        when(rateLimit.tryAcquire(anyString())).thenReturn(true);
        when(sensitive.contains(anyString())).thenReturn(false);
    }

    private PipelineContext ctx(String authorization, String headerUserId, String bodyUser) {
        PipelineContext ctx = new PipelineContext();
        ctx.setAuthorization(authorization);
        ctx.setUserId(headerUserId);
        if (bodyUser != null) {
            ctx.setAgentId(bodyUser);
        }
        ObjectNode request = PipelineContextFactory.mapper().createObjectNode();
        ObjectNode msg = request.putArray("messages").addObject();
        msg.put("role", "user");
        msg.put("content", "hello");
        ctx.setRequest(request);
        return ctx;
    }

    @Test
    void authDisabledSkipsKeyValidationAndUsesBodyUserAsIdentity() {
        props.getAuth().setEnabled(false);
        PipelineContext ctx = ctx(null, "anonymous", "agent-x");

        stage.check(ctx).block();

        assertEquals("agent-x", ctx.getUserId());
        verify(apiKey, never()).validate(anyString());
    }

    @Test
    void authDisabledWithoutBodyUserKeepsHeaderIdentity() {
        props.getAuth().setEnabled(false);
        PipelineContext ctx = ctx(null, "alice", null);

        stage.check(ctx).block();

        assertEquals("alice", ctx.getUserId());
    }

    @Test
    void authEnabledWithoutAuthorizationIsRejected() {
        PipelineContext ctx = ctx(null, "anonymous", "agent-x");

        MasException e = assertThrows(MasException.class, () -> stage.check(ctx).block());
        assertEquals("invalid_api_key", e.getCode());
    }

    @Test
    void authEnabledValidKeyIdentityComesFromKeyAndAgentIdRetained() {
        when(apiKey.validate("sk-good"))
                .thenReturn(Mono.just(new ApiKeyService.ApiKeyInfo("key-user", "key-app", "mas-test")));
        PipelineContext ctx = ctx("Bearer sk-good", "anonymous", "agent-y");

        stage.check(ctx).block();

        assertEquals("key-user", ctx.getUserId());
        assertEquals("key-app", ctx.getAppId());
        // body.user 保留为审计字段，不影响可信身份
        assertEquals("agent-y", ctx.getAgentId());
    }

    @Test
    void authEnabledInvalidKeyIsRejected() {
        when(apiKey.validate("bad")).thenReturn(Mono.error(new IllegalStateException("invalid api key")));
        PipelineContext ctx = ctx("Bearer bad", "anonymous", null);

        MasException e = assertThrows(MasException.class, () -> stage.check(ctx).block());
        assertEquals("invalid_api_key", e.getCode());
    }

    @Test
    void authEnabledEmptyValidateSignalIsRejectedFailClosed() {
        // fail-closed 回归：validate 空完成不得静默放行
        when(apiKey.validate("empty")).thenReturn(Mono.empty());
        PipelineContext ctx = ctx("Bearer empty", "anonymous", null);

        MasException e = assertThrows(MasException.class, () -> stage.check(ctx).block());
        assertEquals("invalid_api_key", e.getCode());
    }
}
