package com.smartdistribution.identity.mail;

import com.smartdistribution.identity.config.AccountProperties;
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
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 寄出設定密碼的 HTML 信，首次設定與重設密碼共用同一個版面，只有標題、說明與按鈕文字不同
 * 在交易成功之後才寄，建立帳號失敗就不會寄出；寄信失敗只記錄錯誤，不讓帳號建立失敗，管理員可以重寄
 * 寄信在背景執行緒進行，API 不用等 SMTP 回應就能回傳
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMailService {

    private static final String SENDER_NAME = "智慧營運管理系統";
    private static final DateTimeFormatter EXPIRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Taipei"));

    private final JavaMailSender mailSender;
    private final AccountProperties accountProperties;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordLinkIssued(PasswordLinkIssuedEvent event) {
        String subject = switch (event.purpose()) {
            case INVITE -> "【智慧營運系統】請設定你的登入密碼";
            case RESET -> "【智慧營運系統】重設密碼";
        };

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(accountProperties.mailFrom(), SENDER_NAME);
            helper.setTo(event.email());
            helper.setSubject(subject);
            helper.setText(htmlOf(event), true);

            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException exception) {
            //信是在帳號存進資料庫之後才寄，這裡丟出例外只會讓 API 回 500，帳號卻已經建好，所以只記錄
            log.error("設定密碼信寄送失敗：{}", event.email(), exception);
        }
    }

    private String htmlOf(PasswordLinkIssuedEvent event) {
        String title;
        String opening;
        String buttonText;

        switch (event.purpose()) {
            case INVITE -> {
                title = "請設定你的登入密碼";
                opening = "系統管理員已經幫你建立帳號，請點下面的按鈕設定登入密碼，設定完成後就可以登入系統。";
                buttonText = "設定密碼";
            }
            case RESET -> {
                title = "重設你的密碼";
                opening = "系統管理員已替你申請重設密碼，請點下面的按鈕設定新密碼。在你設定新密碼之前，原本的密碼仍然可以使用。";
                buttonText = "重設密碼";
            }
            default -> throw new IllegalArgumentException("未知的用途：" + event.purpose());
        }

        String link = accountProperties.frontendBaseUrl() + "/set-password?token=" + event.rawToken();
        String expiresAt = EXPIRY_FORMAT.format(event.expiresAt());

        String name = HtmlUtils.htmlEscape(event.name(), "UTF-8");

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
                        <h1 style="margin:0 0 16px; font-size:22px; color:#111827;">%s</h1>
                        <p style="margin:0 0 12px; font-size:15px; line-height:1.7; color:#374151;">%s 你好：</p>
                        <p style="margin:0 0 28px; font-size:15px; line-height:1.7; color:#374151;">%s</p>
                        <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 28px;">
                          <tr><td style="background-color:#2563eb; border-radius:8px;">
                            <a href="%s" style="display:inline-block; padding:14px 32px; font-size:16px; font-weight:bold; color:#ffffff; text-decoration:none;">%s</a>
                          </td></tr>
                        </table>
                        <div style="margin:0 0 24px; padding:14px 16px; background-color:#fef3c7; border-radius:8px; font-size:14px; line-height:1.6; color:#92400e;">
                          連結在 <strong>%s</strong> 前有效，而且只能使用一次。
                        </div>
                        <p style="margin:0 0 8px; font-size:13px; line-height:1.6; color:#6b7280;">按鈕沒有反應的話，請複製下面的網址貼到瀏覽器：</p>
                        <p style="margin:0 0 24px; font-size:13px; line-height:1.6; word-break:break-all;"><a href="%s" style="color:#2563eb;">%s</a></p>
                        <p style="margin:0; font-size:13px; line-height:1.6; color:#6b7280;">如果你沒有預期收到這封信，請直接忽略，你的帳號不會有任何變動。</p>
                      </td></tr>
                      <tr><td style="padding:16px 32px; background-color:#f9fafb; border-top:1px solid #e5e7eb; font-size:12px; color:#9ca3af;">
                        這封信由系統自動寄出，請不要直接回覆。
                      </td></tr>
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(title, name, opening, link, buttonText, expiresAt, link, link);
    }
}
