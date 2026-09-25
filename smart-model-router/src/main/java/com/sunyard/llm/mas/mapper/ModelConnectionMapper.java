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
 * 模型接入管理（此前 listModelConnections 返回 4 条硬编码、CRUD 只返回 opRecord 不落库）
 */
@Mapper
public interface ModelConnectionMapper {

    @Select("SELECT id, conn_id, name, provider, access_type, endpoint_url, model_id, status, " +
            "latency_ms, created_at, updated_at FROM mas_model_connection ORDER BY id")
    List<Map<String, Object>> listConnections();

    @Select("SELECT id, conn_id, name, provider, access_type, endpoint_url, model_id, status " +
            "FROM mas_model_connection WHERE conn_id = #{connId}")
    Map<String, Object> selectConnection(@Param("connId") String connId);

    @Insert("""
            INSERT INTO mas_model_connection (conn_id, name, provider, access_type, endpoint_url, model_id, status, updated_by)
            VALUES (#{connId}, #{name}, #{provider}, COALESCE(#{accessType},'CLOUD'), #{endpointUrl},
                    #{modelId}, COALESCE(#{status},'ONLINE'), #{updatedBy})
            ON CONFLICT (conn_id) DO NOTHING
            """)
    int insertConnection(@Param("connId") String connId,
                         @Param("name") String name,
                         @Param("provider") String provider,
                         @Param("accessType") String accessType,
                         @Param("endpointUrl") String endpointUrl,
                         @Param("modelId") String modelId,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    @Update("UPDATE mas_model_connection SET name = COALESCE(#{name}, name), " +
            "provider = COALESCE(#{provider}, provider), access_type = COALESCE(#{accessType}, access_type), " +
            "endpoint_url = COALESCE(#{endpointUrl}, endpoint_url), model_id = COALESCE(#{modelId}, model_id), " +
            "status = COALESCE(#{status}, status), updated_by = #{updatedBy}, updated_at = CURRENT_TIMESTAMP " +
            "WHERE conn_id = #{connId}")
    int updateConnection(@Param("connId") String connId,
                         @Param("name") String name,
                         @Param("provider") String provider,
                         @Param("accessType") String accessType,
                         @Param("endpointUrl") String endpointUrl,
                         @Param("modelId") String modelId,
                         @Param("status") String status,
                         @Param("updatedBy") String updatedBy);

    @Delete("DELETE FROM mas_model_connection WHERE conn_id = #{connId}")
    int deleteConnection(@Param("connId") String connId);
}
