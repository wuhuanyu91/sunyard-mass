package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 差异化计价规则（公告一-4/一-5）：
 * 五维费率 —— 部门 dept_id / 系统 app_id / 业务场景 scenario / 服务类型 service_type(模型) / 使用时段 time_range
 * 取代原先散落在 9 处代码里的 0.0016 硬编码单价。
 */
@Mapper
public interface PricingMapper {

    @Select("SELECT id, rule_code, rule_name, dept_id, app_id, scenario, service_type, model_id, " +
            "time_start, time_end, input_price, output_price, request_price, priority, status, " +
            "effective_from, effective_to, created_by, created_at, updated_at " +
            "FROM mas_pricing_rule ORDER BY priority DESC, id")
    List<Map<String, Object>> listRules();

    @Select("SELECT id, rule_code, rule_name, dept_id, app_id, scenario, service_type, model_id, " +
            "time_start, time_end, input_price, output_price, request_price, priority, status " +
            "FROM mas_pricing_rule WHERE rule_code = #{ruleCode}")
    Map<String, Object> selectRule(@Param("ruleCode") String ruleCode);

    @Insert("""
            INSERT INTO mas_pricing_rule
              (rule_code, rule_name, dept_id, app_id, scenario, service_type, model_id,
               time_start, time_end, input_price, output_price, request_price, priority, status,
               effective_from, effective_to, created_by)
            VALUES
              (#{ruleCode}, #{ruleName}, #{deptId}, #{appId}, #{scenario}, #{serviceType}, #{modelId},
               #{timeStart}, #{timeEnd}, #{inputPrice}, #{outputPrice}, #{requestPrice},
               COALESCE(#{priority},0), COALESCE(#{status},1), #{effectiveFrom}, #{effectiveTo}, #{createdBy})
            ON CONFLICT (rule_code) DO NOTHING
            """)
    int insertRule(@Param("ruleCode") String ruleCode,
                   @Param("ruleName") String ruleName,
                   @Param("deptId") String deptId,
                   @Param("appId") String appId,
                   @Param("scenario") String scenario,
                   @Param("serviceType") String serviceType,
                   @Param("modelId") String modelId,
                   @Param("timeStart") String timeStart,
                   @Param("timeEnd") String timeEnd,
                   @Param("inputPrice") java.math.BigDecimal inputPrice,
                   @Param("outputPrice") java.math.BigDecimal outputPrice,
                   @Param("requestPrice") java.math.BigDecimal requestPrice,
                   @Param("priority") Integer priority,
                   @Param("status") Integer status,
                   @Param("effectiveFrom") java.time.LocalDateTime effectiveFrom,
                   @Param("effectiveTo") java.time.LocalDateTime effectiveTo,
                   @Param("createdBy") String createdBy);

    @Update("""
            UPDATE mas_pricing_rule SET
              rule_name    = COALESCE(#{ruleName}, rule_name),
              dept_id      = #{deptId},
              app_id       = #{appId},
              scenario     = #{scenario},
              service_type = #{serviceType},
              model_id     = #{modelId},
              time_start   = #{timeStart},
              time_end     = #{timeEnd},
              input_price  = COALESCE(#{inputPrice}, input_price),
              output_price = COALESCE(#{outputPrice}, output_price),
              request_price= COALESCE(#{requestPrice}, request_price),
              priority     = COALESCE(#{priority}, priority),
              status       = COALESCE(#{status}, status),
              effective_from = #{effectiveFrom},
              effective_to   = #{effectiveTo},
              updated_at   = CURRENT_TIMESTAMP
            WHERE rule_code = #{ruleCode}
            """)
    int updateRule(@Param("ruleCode") String ruleCode,
                   @Param("ruleName") String ruleName,
                   @Param("deptId") String deptId,
                   @Param("appId") String appId,
                   @Param("scenario") String scenario,
                   @Param("serviceType") String serviceType,
                   @Param("modelId") String modelId,
                   @Param("timeStart") String timeStart,
                   @Param("timeEnd") String timeEnd,
                   @Param("inputPrice") java.math.BigDecimal inputPrice,
                   @Param("outputPrice") java.math.BigDecimal outputPrice,
                   @Param("requestPrice") java.math.BigDecimal requestPrice,
                   @Param("priority") Integer priority,
                   @Param("status") Integer status,
                   @Param("effectiveFrom") java.time.LocalDateTime effectiveFrom,
                   @Param("effectiveTo") java.time.LocalDateTime effectiveTo);

    @Delete("DELETE FROM mas_pricing_rule WHERE rule_code = #{ruleCode}")
    int deleteRule(@Param("ruleCode") String ruleCode);
}
