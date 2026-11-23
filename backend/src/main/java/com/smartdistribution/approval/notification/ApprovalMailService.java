package com.smartdistribution.approval.notification;

import com.smartdistribution.approval.entity.ApprovalStatus;
import com.smartdistribution.approval.event.ApprovalDecidedEvent;
import com.smartdistribution.approval.event.ApprovalSubmittedEvent;
import com.smartdistribution.identity.config.AccountProperties;
import com.smartdistribution.identity.service.UserDirectoryService;
import com.smartdistribution.identity.service.UserDirectoryService.UserContact;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.util.HtmlUtils;

import java.io.UnsupportedEncodingException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 核准流程的通知信：送審時通知可以核准的主管，核准或退回時通知建立者與送審者
 * 在交易提交後才寄，核准失敗回滾就不會寄出；寄信失敗只記錄錯誤，不影響核准結果
 * 每位收件人各寄一封，不會讓收件人看到彼此的信箱
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalMailService {

    private static final String SENDER_NAME = "智慧營運管理系統";
    //時區由注入的 Clock 決定，格式化時再套上
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JavaMailSender mailSender;
    private final AccountProperties accountProperties;
    private final UserDirectoryService userDirectoryService;
    private final Clock clock;

    /**
     * 送審後通知有該類型核准權限的啟用中使用者，排除建立者與本輪送審者
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmitted(ApprovalSubmittedEvent event) {
        //建立者與送審者不能核准自己的申請，通知他們沒有意義
        List<UserContact> recipients = userDirectoryService
                .findActiveUsersWithPermission(event.targetType().getPermissionCode())
                .stream()
                .filter(user -> !user.id().equals(event.targetCreatedBy()))
                .filter(user -> !user.id().equals(event.submittedBy()))
                .toList();
        if (recipients.isEmpty()) {
            return;
        }

        String submitterName = nameOf(event.submittedBy());
        String subject = "【智慧營運系統】待核准：" + event.targetLabel();
        String roundNote = event.roundNo() > 1
                ? "第 " + event.roundNo() + " 輪送審（退回後重新送審）"
                : "第 1 輪送審";
        String link = accountProperties.frontendBaseUrl() + "/approvals/" + event.approvalRequestId();

        for (UserContact recipient : recipients) {
            String html = htmlOf(
                    recipient.name(),
                    "有一筆" + event.targetType().getDisplayName() + "等待你核准",
                    List.of(
                            Map.entry("資料", event.targetLabel()),
                            Map.entry("送審者", submitterName),
                            Map.entry("送審時間", format(event.submittedAt())),
                            Map.entry("輪次", roundNote)
                    ),
                    link,
                    "前往核准"
            );
            send(recipient.email(), subject, html);
        }
    }

    /**
     * 核准或退回後通知建立者與本輪送審者，停用的帳號不寄
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDecided(ApprovalDecidedEvent event) {
        //建立者與送審者是同一個人時只寄一封；Set.of 遇到重複元素會丟例外，所以用 HashSet
        Set<Long> requesterIds = new HashSet<>();
        requesterIds.add(event.targetCreatedBy());
        requesterIds.add(event.submittedBy());
        List<UserContact> recipients = userDirectoryService.findActiveContactsByIds(requesterIds);
        if (recipients.isEmpty()) {
            return;
        }

        boolean approved = event.result() == ApprovalStatus.APPROVED;
        String resultText = approved ? "已核准" : "已退回";
        String subject = "【智慧營運系統】" + resultText + "：" + event.targetLabel();
        String commentLabel = approved ? "核准意見" : "退回原因";
        String comment = event.comment() == null ? "（無）" : event.comment();
        String link = accountProperties.frontendBaseUrl()
                + "/approval-targets/" + event.targetType().name() + "/" + event.targetId();

        for (UserContact recipient : recipients) {
            String html = htmlOf(
                    recipient.name(),
                    "你送出的" + event.targetType().getDisplayName() + resultText,
                    List.of(
                            Map.entry("資料", event.targetLabel()),
                            Map.entry("結果", resultText),
                            Map.entry("處理者", nameOf(event.actorId())),
                            Map.entry("處理時間", format(event.decidedAt())),
                            Map.entry(commentLabel, comment)
                    ),
                    link,
                    "查看資料"
            );
            send(recipient.email(), subject, html);
        }
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(accountProperties.mailFrom(), SENDER_NAME);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            //核准已經提交，這裡丟出例外也無法回滾，只記錄下來
            log.error("核准通知信寄送失敗：{}", to, exception);
        }
    }

    private String nameOf(Long userId) {
        String name = userDirectoryService.findName(userId);
        return name == null ? "（未知使用者）" : name;
    }

    private String format(Instant instant) {
        return TIME_FORMAT.withZone(clock.getZone()).format(instant);
    }

    private String htmlOf(
            String recipientName,
            String title,
            List<Map.Entry<String, String>> rows,
            String link,
            String buttonText
    ) {
        StringBuilder rowHtml = new StringBuilder();
        for (Map.Entry<String, String> row : rows) {
            rowHtml.append("""
                    <tr>
                      <td style="padding:8px 12px; width:96px; font-size:14px; color:#6b7280; vertical-align:top;">%s</td>
                      <td style="padding:8px 12px; font-size:14px; color:#111827; white-space:pre-wrap;">%s</td>
                    </tr>
                    """.formatted(escape(row.getKey()), escape(row.getValue())));
        }

        return """
                <!DOCTYPE html>
                <html lang="zh-Hant-TW">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0; padding:0; background-color:#f3f4f6; font-family:'PingFang TC','Microsoft JhengHei','Noto Sans TC',Arial,sans-serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f3f4f6; padding:32px 16px;">
                  <tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                           style="max-width:560px; background-color:#ffffff; border-radius:12px; overflow:hidden; border:1px solid #e5e7eb;">
                      <tr><td style="background-color:#1e3a8a; padding:24px 32px; color:#ffffff; font-size:18px; font-weight:bold; letter-spacing:1px;">
                        智慧營運管理系統
                      </td></tr>
                      <tr><td style="padding:32px;">
                        <h1 style="margin:0 0 16px; font-size:20px; color:#111827;">%s</h1>
                        <p style="margin:0 0 16px; font-size:15px; line-height:1.7; color:#374151;">%s 你好：</p>
                        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"
                               style="margin:0 0 24px; border:1px solid #e5e7eb; border-radius:8px;">
                          %s
                        </table>
                        <table role="presentation" cellpadding="0" cellspacing="0">
                          <tr><td style="background-color:#2563eb; border-radius:8px;">
                            <a href="%s" style="display:inline-block; padding:12px 28px; font-size:15px; font-weight:bold; color:#ffffff; text-decoration:none;">%s</a>
                          </td></tr>
                        </table>
                      </td></tr>
                      <tr><td style="padding:16px 32px; background-color:#f9fafb; border-top:1px solid #e5e7eb; font-size:12px; color:#9ca3af;">
                        這封信由系統自動寄出，請不要直接回覆。
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(escape(title), escape(recipientName), rowHtml, link, escape(buttonText));
    }

    private String escape(String text) {
        return HtmlUtils.htmlEscape(text, "UTF-8");
    }
}
