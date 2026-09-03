# MAS 智能路由项目规则

## 项目概述

智能模型路由访问平台（MAS），基于 L1-L4 四层 Pipeline 处理 LLM 推理请求：

* **L1 规则拦截**：鉴权 + 黑名单 + 敏感词 + 限流

* **L2 多级缓存**：精确缓存 + 语义缓存（命中即短路）

* **L3 意图路由**：意图识别 + 难度分类 + 模型选择

* **L4 执行管控**：转发推理 + 熔断 + 调用日志

## 技术栈

* **Java 17** + **Spring Boot WebFlux**（响应式，非 MVC）

* **MyBatis-Plus**（注解式 SQL，非 XML）

* **PostgreSQL**（pgvector 向量扩展）

* **Caffeine** 本地缓存

* **Reactor** 响应式编程（Mono/Flux）

* **JUnit 5 + Mockito** 单元测试

## 架构约束

### 分层规则

```
Controller (web/) → Service (service/) → Mapper (mapper/) → Entity (entity/)
```

* **Controller**：仅处理 HTTP 请求/响应，返回 `Mono<Map<String, Object>>`

* **Service**：业务逻辑，所有公开方法返回 `Mono<T>`

* **Mapper**：数据访问，使用 `@Select/@Insert/@Update/@Delete` 注解，**不用 XML**

* **Entity**：数据库表映射，使用 `@TableName` + 手动 getter/setter

### 响应式规则

**关键**：MyBatis-Plus 是阻塞调用，写入缓存操作必须用 `ReactiveDbAdapter` 包装：

```java
// ✅ 正确：阻塞调用包装为响应式
return ReactiveDbAdapter.mono(() -> mapper.selectByHash(hash))
        .flatMap(entity -> { /* 业务逻辑 */ });

// ❌ 错误：直接在 Service 中调用阻塞方法
ApiKeyEntity entity = mapper.selectByHash(hash);  // 阻塞 Netty 事件循环！
```

* 有返回值：`ReactiveDbAdapter.mono(() -> mapper.xxx())`

* 无返回值：`ReactiveDbAdapter.monoVoid(() -> mapper.xxx())`

### Pipeline 规则

* **Pipeline 阶段**：`L1RuleInterceptStage` / `L2MultiLevelCacheStage` / `L3IntentRoutingStage` / `L4ExecutionControlStage`

* **阶段职责**：每个阶段只负责自己的检查逻辑，不得越界

* **短路机制**：L2 缓存命中时跳过 L3/L4，直接在 `RoutingPipeline.process()` 中返回

* **上下文传递**：所有阶段通过 `PipelineContext` 传递数据，不得修改方法签名

## 命名约定

### 包名

```
com.sunyard.llm.mas.{layer}
```

layer: `entity` / `mapper` / `service` / `web` / `config` / `pipeline` / `util` / `exception` / `model`

### 类名

* **Entity**：`XxxEntity`（对应表 `mas_xxx`）

* **Mapper**：`XxxMapper`（继承 `BaseMapper<XxxEntity>`）

* **Service**：`XxxService`（`@Service` 注解）

* **Controller**：`XxxController` 或 `XxxAdminController`（`@RestController` 注解）

* **Config**：`XxxProperties` 或 `XxxConfig`

* **Test**：`XxxTest`（单元测试）/ `XxxBoundaryTest`（边界测试）

### 方法名

* **查询**：`selectByXxx` / `listXxx` / `getXxx`

* **创建**：`insertXxx` / `createXxx`

* **更新**：`updateXxx` / `setXxx`

* **删除**：`deleteByXxx` / `revokeXxx`

* **验证**：`validateXxx` / `checkXxx`

### 变量名

* **数据库字段**：下划线命名（`key_hash`, `user_id`, `created_at`）

* **Java 字段**：驼峰命名（`keyHash`, `userId`, `createdAt`）

* **配置项**：短横线命名（`per-user-qps`, `semantic-threshold`）

* **MyBatis-Plus 自动映射**：`map-underscore-to-camel-case: true`

## API 端点规则

### 路径前缀

* **OpenAI 兼容**：`/v1/chat/completions`、`/v1/models`、`/v1/embeddings`

* **内部管理**：`/internal/api-keys`、`/internal/cache/flush`

### 响应格式

Controller 统一返回 `Mono<Map<String, Object>>`：

```java
@PostMapping("/internal/api-keys")
public Mono<Map<String, Object>> createKey(@RequestBody Map<String, Object> body) {
    return service.createKey(...)
            .map(key -> Map.<String, Object>of(
                    "key", key,
                    "message", "Save this key, it will not be shown again"));
}
```

### 错误处理

使用 `MasException` 工厂方法，状态码映射设计文档附录 G.2：

```java
throw MasException.unauthorized();        // 401
throw MasException.blacklisted(userId);   // 403
throw MasException.rateLimited(userId);   // 429
throw MasException.quotaExceeded();       // 402
```

## 配置规则

### 配置类

使用 `@ConfigurationProperties(prefix = "mas")` + 嵌套静态类：

```java
@ConfigurationProperties(prefix = "mas")
public class MasProperties {
    private Backend backend = new Backend();
    private Cache cache = new Cache();
    
    public static class Backend {
        private String defaultEndpoint = "http://localhost:11434/v1";
        private Duration timeout = Duration.ofSeconds(60);
        // getter/setter
    }
}
```

### 配置文件

`application.yml` 中的配置项与 Java 字段一一对应：

```yaml
mas:
  backend:
    default-endpoint: ${MAS_MODEL_ENDPOINT:http://localhost:11434/v1}
    timeout: 60s
  cache:
    exact-ttl: 30m
    semantic-threshold: 0.95
```

## 数据库规则

### 表名

* 统一前缀 `mas_`：`mas_api_key`、`mas_call_log`、`mas_token_quota`

* 下划线命名：`mas_model_config`、`mas_blacklist`

### 字段名

* 下划线命名：`key_hash`、`user_id`、`created_at`

* 主键：`id BIGSERIAL PRIMARY KEY`

* 时间戳：`created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP`

* 状态字段：`status INTEGER`（1=启用，0=禁用）

### DDL 迁移

`schema.sql` 使用幂等语句，支持重复执行：

```sql
ALTER TABLE mas_api_key ADD COLUMN IF NOT EXISTS team_name VARCHAR(128);
```

### SQL 注解

Mapper 使用注解式 SQL，复杂查询用 `<script>` 标签：

```java
@Select({"<script>",
        "SELECT * FROM mas_api_key WHERE 1=1",
        "<if test='teamName != null'> AND team_name = #{teamName}</if>",
        "ORDER BY created_at DESC",
        "</script>"})
List<ApiKeyEntity> listKeys(@Param("teamName") String teamName);
```

## 测试规则

### 测试框架

* **JUnit 5**：`@Test`、`@BeforeEach`

* **Mockito**：`mock()`、`when().thenReturn()`

* **断言**：`assertEquals()`、`assertThrows()`、`assertNotEquals()`

### 测试命名

* **单元测试**：`XxxTest`（正常流程）

* **边界测试**：`XxxBoundaryTest`（边界条件、异常路径）

* **方法名**：描述性命名（`sha256Deterministic`、`unknownKeyRejected`、`expiredKeyRejected`）

### Mock 依赖

Service 测试使用辅助方法构造实例：

```java
private ApiKeyService newService(ApiKeyMapper mapper) {
    QuotaMapper quotaMapper = mock(QuotaMapper.class);
    MasProperties props = new MasProperties();
    return new ApiKeyService(mapper, quotaMapper, props);
}

@Test
void validKeyReturnsBoundIdentity() {
    ApiKeyMapper mapper = mock(ApiKeyMapper.class);
    when(mapper.selectByHash(anyString())).thenReturn(entity);
    ApiKeyService service = newService(mapper);
    
    ApiKeyService.ApiKeyInfo info = service.validate("some-valid-key").block();
    
    assertEquals("alice", info.userId());
}
```

### 响应式测试

使用 `.block()` 同步获取结果：

```java
@Test
void unknownKeyRejected() {
    when(mapper.selectByHash(anyString())).thenReturn(null);
    ApiKeyService service = newService(mapper);
    assertThrows(IllegalStateException.class, () -> service.validate("unknown-key").block());
}
```

## 日志规则

使用 SLF4J：

```java
private static final Logger log = LoggerFactory.getLogger(XxxService.class);

log.info("API Key validated: userId={}, appId={}", info.userId(), info.appId());
log.warn("Rate limit exceeded: userId={}", userId);
log.error("Upstream engine error", exception);
```

## 设计文档引用

代码注释中引用设计文档章节：

```java
/**
 * API Key 鉴权服务（§8 鉴权体系 + §1.4 自助申请增强）：
 * Bearer token → SHA-256 哈希 → 查 DB → Caffeine 缓存(5min) → 返回 userId/appId。
 */
```

* §1.4：智能体接入与 API Key 自助申请

* §2.2：请求处理流程（Pipeline）

* §8：鉴权体系

* 附录 G.2：错误码映射

* 附录 G.6：配置项清单

## 禁止事项

* ❌ **不要**在写入缓存的操作中，在 Service 中直接调用阻塞的 Mapper 方法（必须用 `ReactiveDbAdapter`）

* ❌ **不要**在 Controller 中写业务逻辑（全部下沉到 Service）

* ❌ **不要**使用 Lombok（手动写 getter/setter，保持显式）

* ❌ **不要**使用 XML Mapper（全部用注解式 SQL）

* ❌ **不要**在 Pipeline 阶段外修改 `PipelineContext` 的方法签名

* ❌ **不要**硬编码配置值（全部走 `MasProperties`）

* ❌ **不要**在测试中启动 Spring 容器（纯单元测试，Mock 所有依赖）
