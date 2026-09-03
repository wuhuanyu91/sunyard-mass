package com.sunyard.llm.mas.service;

import com.sunyard.llm.mas.config.MasProperties;
import com.sunyard.llm.mas.entity.ApiKeyEntity;
import com.sunyard.llm.mas.mapper.ApiKeyMapper;
import com.sunyard.llm.mas.mapper.QuotaMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * API Key 服务测试（§8 鉴权体系 + §1.4 自助申请增强）。
 */
class ApiKeyServiceTest {

    private ApiKeyService newService(ApiKeyMapper mapper) {
        QuotaMapper quotaMapper = mock(QuotaMapper.class);
        MasProperties props = new MasProperties();
        return new ApiKeyService(mapper, quotaMapper, props);
    }

    @Test
    void sha256Deterministic() {
        String hash1 = ApiKeyService.sha256("mas-test-key-001");
        String hash2 = ApiKeyService.sha256("mas-test-key-001");
        assertEquals(hash1, hash2);
    }

    @Test
    void sha256DifferentInputsProduceDifferentHashes() {
        String hash1 = ApiKeyService.sha256("key-a");
        String hash2 = ApiKeyService.sha256("key-b");
        assertNotEquals(hash1, hash2);
    }

    @Test
    void sha256KnownValue() {
        // 验证已知哈希值（data.sql 种子数据依赖）
        String expected = "4967cdac0abe235793aadaf37ab545e8c40e01904687e99d87e68a9c4f6c048a";
        assertEquals(expected, ApiKeyService.sha256("mas-test-key-001"));
    }

    @Test
    void sha256EmptyString() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        assertEquals(expected, ApiKeyService.sha256(""));
    }

    @Test
    void unknownKeyRejected() {
        // 回归：selectByHash 返回 null 时 Mono.fromCallable 产生空信号，必须以错误终止，不得静默通过鉴权
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        when(mapper.selectByHash(anyString())).thenReturn(null);
        ApiKeyService service = newService(mapper);
        assertThrows(IllegalStateException.class, () -> service.validate("unknown-key").block());
    }

    @Test
    void validKeyReturnsBoundIdentity() {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setUserId("alice");
        entity.setAppId("app-1");
        entity.setKeyPrefix("mas-test");
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        when(mapper.selectByHash(anyString())).thenReturn(entity);
        ApiKeyService service = newService(mapper);

        ApiKeyService.ApiKeyInfo info = service.validate("some-valid-key").block();

        assertEquals("alice", info.userId());
        assertEquals("app-1", info.appId());
        assertEquals("mas-test", info.keyPrefix());
    }

    @Test
    void expiredKeyRejected() {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setUserId("alice");
        entity.setExpireAt(LocalDateTime.now().minusDays(1));
        ApiKeyMapper mapper = mock(ApiKeyMapper.class);
        when(mapper.selectByHash(anyString())).thenReturn(entity);
        ApiKeyService service = newService(mapper);
        assertThrows(IllegalStateException.class, () -> service.validate("expired-key").block());
    }
}
