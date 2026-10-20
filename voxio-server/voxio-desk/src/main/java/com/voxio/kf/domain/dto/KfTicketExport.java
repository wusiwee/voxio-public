package com.voxio.kf.domain.dto;

import java.util.Date;
import java.util.Map;

import com.voxio.common.annotation.Excel;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 工单导出的行视图（阶段14 批次4 / P2）
 *
 * 导出原来只有前端拼 CSV：服务端不留任何痕迹，「谁把含联系方式的业务数据带走了」查不到。
 * 改成服务端导出后，接口本身带 {@code @Log(businessType = EXPORT)}，操作日志与业务审计各记一条。
 *
 * 列取自管理后台的工单列表视图（Map 查询，联系方式已在服务端解回明文），与页面看到的一致；
 * 差别是服务端导出按**当前筛选条件的全部结果**出，而不是只有页面上这一页——
 * 这也是把导出挪到服务端的实际收益之一。正文与推送响应不在列表 SQL 里（阶段11 为省带宽去掉），
 * 导出同样不带：要看全文走详情接口，那条路径有它自己的留痕。
 *
 * @author voxio
 */
@Getter
@Setter
@ToString
public class KfTicketExport
{
    /** 工单编号 */
    @Excel(name = "工单编号")
    private String ticketNo;

    /** 标题 */
    @Excel(name = "标题", width = 40d)
    private String title;

    /** 状态（已翻译成中文） */
    @Excel(name = "状态")
    private String status;

    /** 类型 */
    @Excel(name = "类型")
    private String category;

    /** 优先级 */
    @Excel(name = "优先级")
    private String priority;

    /** 来源 */
    @Excel(name = "来源")
    private String source;

    /** 联系方式（明文导出：接收方是运营人员，密文对他们没有意义） */
    @Excel(name = "联系方式")
    private String contact;

    /** 处理客服 */
    @Excel(name = "处理客服")
    private String handlerNick;

    /** 关联会话号 */
    @Excel(name = "会话号")
    private String sessionNo;

    /** 提交人 */
    @Excel(name = "提交人")
    private String createBy;

    /** 创建时间 */
    @Excel(name = "创建时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /** 完成时间 */
    @Excel(name = "完成时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date finishTime;

    /** 关闭时间 */
    @Excel(name = "关闭时间", dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date closeTime;

    /** 状态字典（与页面标签同源，导出口径翻成人话） */
    private static final Map<String, String> STATUS_TEXT = Map.of("0", "待处理", "1", "处理中", "2", "已解决",
            "3", "已关闭");

    private static final Map<String, String> CATEGORY_TEXT = Map.of("consult", "咨询", "complaint", "投诉",
            "bug", "故障", "other", "其他");

    private static final Map<String, String> PRIORITY_TEXT = Map.of("1", "低", "2", "中", "3", "高");

    private static final Map<String, String> SOURCE_TEXT = Map.of("1", "访客端", "2", "客服代建", "3", "后台新建");

    /** 由列表视图行构造导出行；字典值统一翻译成中文，避免报表里出现看不懂的代码 */
    public static KfTicketExport from(Map<String, Object> row)
    {
        KfTicketExport export = new KfTicketExport();
        export.setTicketNo(text(row.get("ticketNo")));
        export.setTitle(text(row.get("title")));
        export.setStatus(STATUS_TEXT.getOrDefault(text(row.get("status")), text(row.get("status"))));
        export.setCategory(CATEGORY_TEXT.getOrDefault(text(row.get("category")), text(row.get("category"))));
        export.setPriority(PRIORITY_TEXT.getOrDefault(text(row.get("priority")), text(row.get("priority"))));
        export.setSource(SOURCE_TEXT.getOrDefault(text(row.get("source")), text(row.get("source"))));
        export.setContact(text(row.get("contact")));
        export.setHandlerNick(text(row.get("handlerNick")));
        export.setSessionNo(text(row.get("sessionNo")));
        export.setCreateBy(text(row.get("createBy")));
        export.setCreateTime(date(row.get("createTime")));
        export.setFinishTime(date(row.get("finishTime")));
        export.setCloseTime(date(row.get("closeTime")));
        return export;
    }

    private static String text(Object value)
    {
        return value == null ? "" : String.valueOf(value);
    }

    private static Date date(Object value)
    {
        if (value instanceof Date d)
        {
            return d;
        }
        if (value instanceof java.time.LocalDateTime ldt)
        {
            return Date.from(ldt.atZone(java.time.ZoneId.systemDefault()).toInstant());
        }
        return null;
    }
}
