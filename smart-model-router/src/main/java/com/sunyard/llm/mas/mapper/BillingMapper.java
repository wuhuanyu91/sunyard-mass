package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 计费结算与对账（公告一-4 技术要求点名"计量采集、计费结算与对账业务"）：
 * 账单 + 账单明细 + 账期锁账 + 对账记录。
 * 说明：金额优先取请求时计价引擎写入的 cost_amount；存量数据回退按 0.0016 元/token 折算。
 */
@Mapper
public interface BillingMapper {

    /** 按账期 × 租户 聚合生成账单主体 */
    @Select("""
            SELECT COALESCE(tenant_id, 'UNKNOWN') AS tenant_id,
                   COUNT(*)                        AS total_calls,
                   COALESCE(SUM(total_tokens), 0)  AS total_tokens,
                   COALESCE(SUM(COALESCE(cost_amount, total_tokens::numeric * 0.0016)), 0) AS total_amount
            FROM mas_call_log
            WHERE bill_month = #{billMonth}
              AND COALESCE(billed, 0) = 0
            GROUP BY COALESCE(tenant_id, 'UNKNOWN')
            """)
    List<Map<String, Object>> aggregateByTenant(@Param("billMonth") String billMonth);

    /** 账单明细：按 租户×应用×模型×场景 拆分 */
    @Select("""
            SELECT COALESCE(app_id, 'UNKNOWN')    AS app_id,
                   COALESCE(model_id, 'UNKNOWN')  AS model_id,
                   COALESCE(scenario, 'UNKNOWN')  AS scenario,
                   COALESCE(service_type, 'chat') AS service_type,
                   COUNT(*)                       AS calls,
                   COALESCE(SUM(prompt_tokens), 0)     AS input_tokens,
                   COALESCE(SUM(completion_tokens), 0) AS output_tokens,
                   COALESCE(SUM(COALESCE(cost_amount, total_tokens::numeric * 0.0016)), 0) AS amount
            FROM mas_call_log
            WHERE bill_month = #{billMonth}
              AND COALESCE(tenant_id, 'UNKNOWN') = #{tenantId}
              AND COALESCE(billed, 0) = 0
            GROUP BY 1, 2, 3, 4
            ORDER BY amount DESC
            """)
    List<Map<String, Object>> aggregateItems(@Param("billMonth") String billMonth,
                                             @Param("tenantId") String tenantId);

    @Insert("""
            INSERT INTO mas_bill (bill_no, bill_month, tenant_id, dept_id, total_calls, total_tokens,
                                  total_amount, status)
            VALUES (#{billNo}, #{billMonth}, #{tenantId}, #{deptId}, #{totalCalls}, #{totalTokens},
                    #{totalAmount}, 'DRAFT')
            ON CONFLICT (bill_month, tenant_id) DO UPDATE SET
                total_calls  = EXCLUDED.total_calls,
                total_tokens = EXCLUDED.total_tokens,
                total_amount = EXCLUDED.total_amount,
                status       = CASE WHEN mas_bill.status IN ('LOCKED','SETTLED')
                                    THEN mas_bill.status ELSE 'DRAFT' END
            """)
    int upsertBill(@Param("billNo") String billNo,
                   @Param("billMonth") String billMonth,
                   @Param("tenantId") String tenantId,
                   @Param("deptId") String deptId,
                   @Param("totalCalls") long totalCalls,
                   @Param("totalTokens") long totalTokens,
                   @Param("totalAmount") java.math.BigDecimal totalAmount);

    @Insert("""
            INSERT INTO mas_bill_item (bill_no, app_id, model_id, scenario, service_type,
                                       calls, input_tokens, output_tokens, amount)
            VALUES (#{billNo}, #{appId}, #{modelId}, #{scenario}, #{serviceType},
                    #{calls}, #{inputTokens}, #{outputTokens}, #{amount})
            """)
    int insertBillItem(@Param("billNo") String billNo,
                       @Param("appId") String appId,
                       @Param("modelId") String modelId,
                       @Param("scenario") String scenario,
                       @Param("serviceType") String serviceType,
                       @Param("calls") long calls,
                       @Param("inputTokens") long inputTokens,
                       @Param("outputTokens") long outputTokens,
                       @Param("amount") java.math.BigDecimal amount);

    @Select("SELECT bill_no FROM mas_bill WHERE bill_month = #{billMonth} AND tenant_id = #{tenantId}")
    String selectBillNo(@Param("billMonth") String billMonth,
                        @Param("tenantId") String tenantId);

    /** 锁账：该账期的调用记录置为已入账，此后不再参与聚合 */
    @Update("""
            UPDATE mas_call_log SET billed = 1
            WHERE bill_month = #{billMonth}
              AND COALESCE(billed, 0) = 0
              AND COALESCE(tenant_id, 'UNKNOWN') = #{tenantId}
            """)
    int markBilled(@Param("billMonth") String billMonth,
                   @Param("tenantId") String tenantId);

    @Update("UPDATE mas_bill SET status = #{status}, locked_at = CURRENT_TIMESTAMP, locked_by = #{operator} " +
            "WHERE bill_no = #{billNo}")
    int updateBillStatus(@Param("billNo") String billNo,
                         @Param("status") String status,
                         @Param("operator") String operator);

    @Select({"<script>",
            "SELECT id, bill_no, bill_month, tenant_id, dept_id, total_calls, total_tokens, total_amount,",
            "  status, locked_at, locked_by, created_at FROM mas_bill WHERE 1=1",
            "<if test='billMonth != null'> AND bill_month = #{billMonth}</if>",
            "<if test='tenantId != null'> AND tenant_id = #{tenantId}</if>",
            "ORDER BY bill_month DESC, tenant_id",
            "</script>"})
    List<Map<String, Object>> listBills(@Param("billMonth") String billMonth,
                                        @Param("tenantId") String tenantId);

    @Select("SELECT id, bill_no, app_id, model_id, scenario, service_type, calls, input_tokens, " +
            "output_tokens, amount FROM mas_bill_item WHERE bill_no = #{billNo} ORDER BY amount DESC")
    List<Map<String, Object>> listBillItems(@Param("billNo") String billNo);

    // ---------------- 对账 ----------------

    @Insert("""
            INSERT INTO mas_reconciliation (bill_month, tenant_id, platform_amount, upstream_amount,
                                            diff_amount, diff_ratio, result, remark, operator)
            VALUES (#{billMonth}, #{tenantId}, #{platformAmount}, #{upstreamAmount},
                    #{diffAmount}, #{diffRatio}, #{result}, #{remark}, #{operator})
            """)
    int insertReconciliation(@Param("billMonth") String billMonth,
                             @Param("tenantId") String tenantId,
                             @Param("platformAmount") java.math.BigDecimal platformAmount,
                             @Param("upstreamAmount") java.math.BigDecimal upstreamAmount,
                             @Param("diffAmount") java.math.BigDecimal diffAmount,
                             @Param("diffRatio") java.math.BigDecimal diffRatio,
                             @Param("result") String result,
                             @Param("remark") String remark,
                             @Param("operator") String operator);

    @Select({"<script>",
            "SELECT id, bill_month, tenant_id, platform_amount, upstream_amount, diff_amount,",
            "  diff_ratio, result, remark, operator, created_at FROM mas_reconciliation WHERE 1=1",
            "<if test='billMonth != null'> AND bill_month = #{billMonth}</if>",
            "<if test='tenantId != null'> AND tenant_id = #{tenantId}</if>",
            "ORDER BY created_at DESC LIMIT 200",
            "</script>"})
    List<Map<String, Object>> listReconciliations(@Param("billMonth") String billMonth,
                                                  @Param("tenantId") String tenantId);
}
