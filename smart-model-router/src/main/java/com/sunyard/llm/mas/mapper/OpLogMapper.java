package com.sunyard.llm.mas.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 操作审计留痕（公告二-8）：此前 15 个写端点返回的 opRecord 用完即丢，现统一落库。
 */
@Mapper
public interface OpLogMapper {

    @Insert("INSERT INTO mas_op_log (op_id, op_type, op_module, operator, target_id, detail, " +
            "before_json, after_json, result, client_ip) " +
            "VALUES (#{opId}, #{opType}, #{opModule}, #{operator}, #{targetId}, #{detail}, " +
            "#{beforeJson}, #{afterJson}, #{result}, #{clientIp})")
    int insert(@Param("opId") String opId,
               @Param("opType") String opType,
               @Param("opModule") String opModule,
               @Param("operator") String operator,
               @Param("targetId") String targetId,
               @Param("detail") String detail,
               @Param("beforeJson") String beforeJson,
               @Param("afterJson") String afterJson,
               @Param("result") String result,
               @Param("clientIp") String clientIp);

    @Select({"<script>",
            "SELECT op_id, op_type, op_module, operator, target_id, detail, result, client_ip, created_at",
            "FROM mas_op_log WHERE 1=1",
            "<if test='opModule != null'> AND op_module = #{opModule}</if>",
            "<if test='operator != null'> AND operator = #{operator}</if>",
            "<if test='targetId != null'> AND target_id = #{targetId}</if>",
            "ORDER BY created_at DESC LIMIT #{limit} OFFSET #{offset}",
            "</script>"})
    List<Map<String, Object>> list(@Param("opModule") String opModule,
                                   @Param("operator") String operator,
                                   @Param("targetId") String targetId,
                                   @Param("limit") int limit,
                                   @Param("offset") int offset);

    @Select({"<script>",
            "SELECT COUNT(*) FROM mas_op_log WHERE 1=1",
            "<if test='opModule != null'> AND op_module = #{opModule}</if>",
            "<if test='operator != null'> AND operator = #{operator}</if>",
            "</script>"})
    long count(@Param("opModule") String opModule,
               @Param("operator") String operator);
}
