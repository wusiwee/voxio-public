package com.voxio.kf.migration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.alibaba.fastjson2.JSON;
import com.voxio.common.utils.StringUtils;
import com.voxio.common.utils.crypto.SmCodec;
import com.voxio.common.utils.crypto.SmFieldCipher;
import com.voxio.common.utils.crypto.SmKeyStore;
import com.voxio.common.utils.crypto.Sm4Utils;
import com.voxio.kf.mapper.KfMigrationMapper;
import com.voxio.kf.service.IKfSecurityService;
import com.voxio.kf.service.IKfSessionKeyService;

import lombok.RequiredArgsConstructor;

/**
 * 存量明文迁移与回滚（阶段10.6 批次2 / P0-2）
 *
 * 10.2 与 10.3 只改了写路径：新数据落密文，库里此前的手机号、邮箱、联系方式、正文与会话消息仍是明文。
 * 这个入口把那些存量按「与运行时完全相同的加密路径」补加密，使读路径不再需要保留明文兼容分支。
 *
 * 三条硬约束决定了它的形态：
 * 1. 必须复用运行时加密入口（{@link SmFieldCipher#encrypt} 与会话子钥 {@code encryptForStore}），
 *    否则迁出来的密文运行时解不开，等于把数据迁坏；
 * 2. 迁移前必须先落一份可解开的备份并当场回读校验，"不带备份迁移"被明确禁止；
 * 3. 只改列值，不改表结构、不增删行——所以每个目标都要做「行数不变、密文增量 = 迁移量、剩余明文 = 0」的闭环核对。
 *
 * 只在带 {@code --kf.migrate=} 参数时动作，正常启动直接跳过：
 * 
 * <pre>
 * --kf.migrate=legacy-plaintext [--kf.migrate.dry-run=true] [--kf.migrate.backup-dir=...]
 * --kf.migrate=rollback --kf.migrate.backup=&lt;上面生成的 .jsonl.enc 文件&gt;
 * </pre>
 *
 * @author voxio
 */
@Component
@RequiredArgsConstructor
public class LegacyDataMigrationRunner implements ApplicationRunner
{
    private static final Logger log = LoggerFactory.getLogger("kf.security");

    /** 启动参数键：不带该参数时本类不做任何事 */
    public static final String MODE_KEY = "kf.migrate";

    /** 迁移模式 */
    public static final String MODE_MIGRATE = "legacy-plaintext";

    /** 回滚模式 */
    public static final String MODE_ROLLBACK = "rollback";

    /** 备份文件与密文载荷的加密用途（与 field/media/msg-store/config-credential 都不同串） */
    public static final String BACKUP_PURPOSE = "migration-backup";

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    /** 按业务字段子钥（实体上挂了 SmFieldCipherTypeHandler）迁移 */
    private static final int KIND_FIELD = 0;

    /** 按会话派生落库子钥迁移（kf_message.content 与运行时 KfMessageServiceImpl 同一口径） */
    private static final int KIND_SESSION = 1;

    /**
     * 迁移白名单：表、主键列、目标列、辅助列（无则写死字面量 null）、加密口径。
     * 这里是唯一允许的标识符来源，任何外部输入都不会拼进 SQL。
     */
    private static final List<Target> TARGETS = List.of(
            new Target("kf_message", "msg_id", "content", "session_id", KIND_SESSION),
            new Target("kf_agent", "agent_id", "phone", "null", KIND_FIELD),
            new Target("kf_agent", "agent_id", "email", "null", KIND_FIELD),
            new Target("kf_visitor", "visitor_id", "contact_phone", "null", KIND_FIELD),
            new Target("kf_visitor", "visitor_id", "contact_email", "null", KIND_FIELD),
            new Target("kf_ticket", "ticket_id", "contact", "null", KIND_FIELD),
            new Target("kf_ticket", "ticket_id", "content", "null", KIND_FIELD),
            new Target("kf_ticket_reply", "reply_id", "content", "null", KIND_FIELD),
            new Target("kf_offline_message", "msg_id", "contact", "null", KIND_FIELD),
            new Target("kf_offline_message", "msg_id", "content", "null", KIND_FIELD));

    private final KfMigrationMapper migrationMapper;

    private final SmFieldCipher fieldCipher;

    private final IKfSessionKeyService sessionKeyService;

    private final IKfSecurityService securityService;

    private final SmKeyStore keyStore;

    private final Environment environment;

    private final ConfigurableApplicationContext context;

    @Value("${kf.migrate.backup-dir:${user.home}/voxio/backup}")
    private String backupDir;

    /** 迁移白名单条目 */
    private record Target(String table, String pk, String column, String extra, int kind)
    {
    }

    @Override
    public void run(ApplicationArguments args)
    {
        String mode = environment.getProperty(MODE_KEY);
        if (StringUtils.isEmpty(mode))
        {
            return;
        }
        boolean dryRun = Boolean.parseBoolean(environment.getProperty("kf.migrate.dry-run", "false"));
        int code;
        try
        {
            if (MODE_MIGRATE.equals(mode))
            {
                migrate(dryRun);
                code = 0;
            }
            else if (MODE_ROLLBACK.equals(mode))
            {
                rollback(requiredBackupFile());
                code = 0;
            }
            else
            {
                log.error("[存量迁移] 未知模式 {}（只支持 {} / {}）", mode, MODE_MIGRATE, MODE_ROLLBACK);
                code = 2;
            }
        }
        catch (Exception e)
        {
            log.error("[存量迁移] {} 失败：{}", mode, e.getMessage(), e);
            code = 1;
        }
        // 这是一次性运维入口，跑完就退出：留着进程等于白占 10096 端口与数据库连接
        context.close();
        System.exit(code);
    }

    /** 迁移（或 dry-run 只出统计） */
    void migrate(boolean dryRun) throws Exception
    {
        if (!securityService.isEnabled())
        {
            throw new IllegalStateException("voxio.crypto.enabled=false 时 encryptForStore/encrypt 原样返回明文，"
                    + "迁移会把明文再写一遍当成已完成；请恢复加密开关后再迁");
        }
        // 1) 先只做统计，确认「有没有东西要迁」再决定要不要落备份，避免每次空跑都产一个没内容的备份文件
        Map<String, int[]> before = new LinkedHashMap<>();
        int totalPlain = 0;
        for (Target target : TARGETS)
        {
            int[] snapshot = new int[]
            { migrationMapper.countRows(target.table()), migrationMapper.countFilled(target.table(), target.column()),
                    migrationMapper.countCipher(target.table(), target.column()),
                    migrationMapper.countPlain(target.table(), target.column()) };
            before.put(target.table() + "." + target.column(), snapshot);
            totalPlain += snapshot[3];
            log.info("[存量迁移] {}.{} 行数={} 非空={} 密文={} 明文={}", target.table(), target.column(), snapshot[0],
                    snapshot[1], snapshot[2], snapshot[3]);
        }
        // 库里已是密文、但 encrypt_flag 还是 0 的消息行：读路径按标记决定是否解密，漏对齐就等于把密文端给客户
        int mislabelledBefore = migrationMapper.selectMislabelledMessages().size();
        // 2) 迁移前必须先有一份能解开的备份（禁止事项第一条：不带备份迁移）
        Path backupFile = null;
        if (!dryRun && (totalPlain > 0 || mislabelledBefore > 0))
        {
            backupFile = writeBackup();
        }
        else if (!dryRun)
        {
            log.info("[存量迁移] 白名单列已全部是密文形态且标记已对齐，本轮无需迁移，也就不再落新备份");
        }
        List<Map<String, Object>> report = new ArrayList<>();
        int totalMigrated = 0;
        long closureDiff = 0;
        for (Target target : TARGETS)
        {
            int[] snapshot = before.get(target.table() + "." + target.column());
            int migrated = 0;
            if (!dryRun && snapshot[3] > 0)
            {
                migrated = migrateTarget(target);
            }
            int cipherAfter = migrationMapper.countCipher(target.table(), target.column());
            int plainAfter = migrationMapper.countPlain(target.table(), target.column());
            int rowsAfter = migrationMapper.countRows(target.table());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("table", target.table());
            item.put("column", target.column());
            item.put("rows", snapshot[0]);
            item.put("cipher_before", snapshot[2]);
            item.put("plain_before", snapshot[3]);
            item.put("migrated", migrated);
            item.put("cipher_after", cipherAfter);
            item.put("plain_after", plainAfter);
            item.put("rows_after", rowsAfter);
            // 闭环：迁掉的每一条都必须变成密文，且不允许多出来——差值非 0 就是没迁干净或写坏了
            long diff = (long) cipherAfter - snapshot[2] - migrated;
            item.put("closure_diff", diff);
            if (!dryRun)
            {
                closureDiff += Math.abs(diff) + plainAfter + (rowsAfter - snapshot[0]);
            }
            totalMigrated += migrated;
            report.add(item);
        }
        int aligned = dryRun ? 0 : alignMessageFlags();
        int mislabelledAfter = migrationMapper.selectMislabelledMessages().size();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mode", dryRun ? "dry-run" : MODE_MIGRATE);
        summary.put("at", LocalDateTime.now().toString());
        summary.put("plain_before_total", totalPlain);
        summary.put("migrated_total", totalMigrated);
        summary.put("flag_aligned", aligned);
        summary.put("mislabelled_before", mislabelledBefore);
        summary.put("mislabelled_after", mislabelledAfter);
        summary.put("closure_diff", closureDiff);
        summary.put("cipher_prefix", SmCodec.STORE_PREFIX + keyStore.getKeyVersion() + "$");
        summary.put("targets", TARGETS.size());
        summary.put("backup_file", backupFile == null ? "" : backupFile.toString());
        summary.put("not_upgraded_password_agents", migrationMapper.selectNotUpgradedAgents());
        summary.put("items", report);
        String json = JSON.toJSONString(summary);
        System.out.println("[存量迁移报告] " + json);
        if (!dryRun)
        {
            Path file = backupDir().resolve("kf-legacy-report-" + LocalDateTime.now().format(STAMP) + ".json");
            Files.writeString(file, json, StandardCharsets.UTF_8);
            System.out.println("[存量迁移报告] 已写入 " + file);
        }
        if (!dryRun && (closureDiff != 0 || mislabelledAfter != 0))
        {
            throw new IllegalStateException("迁移闭环不为 0（差值合计 " + closureDiff + "，未对齐标记行 "
                    + mislabelledAfter + "），请核对上面的报告");
        }
    }

    /**
     * 把「内容已是密文但 encrypt_flag 不为 1」的消息行对齐标记。
     *
     * 这批行不是本次迁移造成的：10.2 的异步落库与部分系统消息写入时只写了密文没写标记。
     * 迁移若不顺手对齐，读路径依旧按标记跳过解密，客户看到的就是 sm4-gcm$… 这串东西。
     */
    private int alignMessageFlags()
    {
        int aligned = 0;
        for (Map<String, Object> row : migrationMapper.selectMislabelledMessages())
        {
            // key_version 以密文前缀里的版本为准（前缀才是运行时解密真正依据的那位）
            int keyVersion = SmCodec.storedKeyVersion((String) row.get("col_value"));
            migrationMapper.markMessageCipher(row.get("pk_value"),
                    keyVersion < 0 ? keyStore.getKeyVersion() : keyVersion);
            aligned++;
        }
        return aligned;
    }

    /** 迁移一个目标列：明文 → 运行时同一口径的密文 */
    private int migrateTarget(Target target) throws Exception
    {
        List<Map<String, Object>> rows = target.kind() == KIND_SESSION
                ? migrationMapper.selectMessageRows()
                : migrationMapper.selectAllRows(target.table(), target.pk(), target.column(), target.extra());
        int migrated = 0;
        for (Map<String, Object> row : rows)
        {
            Object pkValue = row.get("pk_value");
            String value = (String) row.get("col_value");
            if (value == null || value.isEmpty() || SmCodec.isStoredCipher(value))
            {
                continue;
            }
            String cipher = target.kind() == KIND_SESSION
                    ? sessionKeyService.encryptForStore(toLong(row.get("extra_value")), value)
                    : fieldCipher.encrypt(value);
            if (!SmCodec.isStoredCipher(cipher))
            {
                throw new IllegalStateException(target.table() + "." + target.column() + " 加密结果不是密文形态，拒绝写库");
            }
            int affected = target.kind() == KIND_SESSION
                    ? migrationMapper.updateMessageCipher(pkValue, cipher, SmCodec.storedKeyVersion(cipher))
                    : migrationMapper.updateValue(target.table(), target.pk(), target.column(), pkValue, cipher);
            if (affected != 1)
            {
                throw new IllegalStateException(target.table() + " 主键 " + pkValue + " 更新影响行数不为 1");
            }
            migrated++;
        }
        return migrated;
    }

    /**
     * 迁移前的全列备份：把每个目标列的「此刻库里的原值」全部导出（含已是密文的行），
     * 用 migration-backup 子钥 SM4-GCM 封成单文件——备份内容本身含个人信息，明文落盘等于换个地方泄漏。
     */
    private Path writeBackup() throws Exception
    {
        StringBuilder jsonl = new StringBuilder();
        int entries = 0;
        for (Target target : TARGETS)
        {
            List<Map<String, Object>> rows = target.kind() == KIND_SESSION
                    ? migrationMapper.selectMessageRows()
                    : migrationMapper.selectAllRows(target.table(), target.pk(), target.column(), target.extra());
            for (Map<String, Object> row : rows)
            {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("table", target.table());
                entry.put("pk", target.pk());
                entry.put("column", target.column());
                entry.put("pk_value", row.get("pk_value"));
                entry.put("value", row.get("col_value"));
                if (target.kind() == KIND_SESSION)
                {
                    // 消息行要连标记列一起带走，回滚才能回到「迁移前那一刻库里到底是什么样」
                    entry.put("flag", row.get("flag_value"));
                    entry.put("algo", row.get("algo_value"));
                    entry.put("key_version", row.get("kv_value"));
                }
                jsonl.append(JSON.toJSONString(entry)).append('\n');
                entries++;
            }
        }
        byte[] key = SmCodec.unhex(keyStore.deriveKeyHex(BACKUP_PURPOSE, SmCodec.SM4_KEY_BYTES));
        String packed = Sm4Utils.encryptGcm(key, jsonl.toString().getBytes(StandardCharsets.UTF_8));
        String body = SmCodec.wrapStored(packed, keyStore.getKeyVersion());
        Path file = backupDir().resolve("kf-legacy-backup-" + LocalDateTime.now().format(STAMP) + ".jsonl.enc");
        Files.writeString(file, body, StandardCharsets.UTF_8);
        // 回读校验：备份写完了但解不开，等于没有备份
        String restored = new String(Sm4Utils.decryptGcm(key,
                SmCodec.unbase64(SmCodec.storedPayload(Files.readString(file, StandardCharsets.UTF_8)))),
                StandardCharsets.UTF_8);
        if (countLines(restored) != entries)
        {
            Files.deleteIfExists(file);
            throw new IllegalStateException("备份回读校验失败（期望 " + entries + " 行），已删除该备份并中止迁移");
        }
        System.out.println("[存量迁移] 备份完成：" + entries + " 行 → " + file);
        return file;
    }

    /** 按备份文件回滚：把每个主键的列值原样写回迁移前的形态（明文就是明文） */
    void rollback(Path backupFile) throws Exception
    {
        byte[] key = SmCodec.unhex(keyStore.deriveKeyHex(BACKUP_PURPOSE, SmCodec.SM4_KEY_BYTES));
        String body = Files.readString(backupFile, StandardCharsets.UTF_8);
        String payload = SmCodec.storedPayload(body);
        if (payload == null)
        {
            throw new IllegalStateException("备份文件形态不是 " + SmCodec.STORE_PREFIX + "，拒绝回滚");
        }
        String jsonl = new String(Sm4Utils.decryptGcm(key, SmCodec.unbase64(payload)), StandardCharsets.UTF_8);
        int restored = 0;
        int skipped = 0;
        for (String line : jsonl.split("\n"))
        {
            if (line.isBlank())
            {
                continue;
            }
            Map<String, Object> entry = JSON.parseObject(line);
            Target target = findTarget((String) entry.get("table"), (String) entry.get("column"));
            String value = entry.get("value") == null ? null : String.valueOf(entry.get("value"));
            if (target == null || value == null)
            {
                // 原值是 NULL 的行迁移本就没碰过（countPlain 只算非空），回滚也没必要动它
                skipped++;
                continue;
            }
            if ("kf_message".equals(target.table()))
            {
                migrationMapper.restoreMessageRow(entry.get("pk_value"), value, str(entry.get("flag")),
                        str(entry.get("algo")), intOf(entry.get("key_version")));
            }
            else
            {
                migrationMapper.updateValue(target.table(), target.pk(), target.column(), entry.get("pk_value"), value);
            }
            restored++;
        }
        System.out.println("[存量迁移回滚] 来源 " + backupFile + " 还原 " + restored + " 行，跳过 " + skipped
                + " 行（原值为 NULL，迁移未曾改动）");
    }

    private String str(Object value)
    {
        return value == null ? null : String.valueOf(value);
    }

    private Integer intOf(Object value)
    {
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private int countLines(String text)
    {
        int n = 0;
        for (String line : text.split("\n"))
        {
            if (!line.isBlank())
            {
                n++;
            }
        }
        return n;
    }

    private Target findTarget(String table, String column)
    {
        for (Target target : TARGETS)
        {
            if (target.table().equals(table) && target.column().equals(column))
            {
                return target;
            }
        }
        return null;
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            throw new IllegalStateException("消息行缺少 session_id，无法按会话子钥加密");
        }
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private Path requiredBackupFile()
    {
        String path = environment.getProperty("kf.migrate.backup");
        if (StringUtils.isEmpty(path))
        {
            throw new IllegalStateException("回滚需要 --kf.migrate.backup=<迁移时生成的 .jsonl.enc 文件>");
        }
        Path file = Paths.get(path).toAbsolutePath().normalize();
        if (!Files.isReadable(file))
        {
            throw new IllegalStateException("备份文件读不到：" + file);
        }
        return file;
    }

    private Path backupDir() throws Exception
    {
        Path dir = Paths.get(SmKeyStore.expandHome(backupDir)).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        return dir;
    }
}
