package com.sunyard.llm.mas.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * API Key 服务测试（§8 已知限制消除 — 鉴权体系）。
 */
class ApiKeyServiceTest {

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
}
