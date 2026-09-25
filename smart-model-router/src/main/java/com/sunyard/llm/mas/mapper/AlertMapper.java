package com.sunyard.llm.mas.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sunyard.llm.mas.entity.AlertEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

/**
 * 告警（公告二-7）：处置闭环 —— 确认 → 开始处置 → 关闭，含处置意见留痕。
 */
@Mapper
public interface AlertMapper extends BaseMapper<AlertEntity> {

    @Select("SELECT alert_id, alert_status, event_level, title, detail, trace_id, created_at " +
            "FROM mas_alert ORDER BY created_at DESC LIMIT 200")
    List<Map<String, Object>> listAlerts();

    @Update("UPDATE mas_alert SET alert_status = #{status}, " +
            "detail = CASE WHEN #{comment} IS NULL THEN detail " +
            "ELSE COALESCE(detail,'') || ' | 处置意见: ' || #{comment} END " +
            "WHERE alert_id = #{alertId}")
    int updateAlertStatus(@Param("alertId") String alertId,
                          @Param("status") String status,
                          @Param("comment") String comment);

    @Select("SELECT alert_id FROM mas_alert WHERE alert_id = #{alertId}")
    String exists(@Param("alertId") String alertId);
}
