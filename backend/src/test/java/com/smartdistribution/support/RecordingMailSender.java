package com.smartdistribution.support;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 測試用的寄信元件：只把信記下來，不會真的寄出
 * 測試可以檢查寄給誰、標題與 HTML 內容，並從信裡取出設定密碼的 token
 */
public class RecordingMailSender extends JavaMailSenderImpl {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([A-Za-z0-9_-]+)");

    private final List<RecordedMail> mails = new CopyOnWriteArrayList<>();

    public record RecordedMail(List<String> to, String subject, String html) {
    }

    //所有寄信方法最後都會走到這裡，攔下來記錄，不連線到 SMTP
    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            mails.add(toRecordedMail(message));
        }
    }

    public void clear() {
        mails.clear();
    }

    public List<RecordedMail> messagesTo(String email) {
        return mails.stream()
                .filter(mail -> mail.to().contains(email))
                .toList();
    }

    //寄給這個 Email 的最後一封信裡的 token
    public String latestTokenFor(String email) {
        List<RecordedMail> received = messagesTo(email);
        if (received.isEmpty()) {
            throw new AssertionError("沒有寄給 " + email + " 的信");
        }

        Matcher matcher = TOKEN_PATTERN.matcher(received.getLast().html());
        if (!matcher.find()) {
            throw new AssertionError("信裡找不到 token");
        }

        return matcher.group(1);
    }

    private RecordedMail toRecordedMail(MimeMessage message) {
        try {
            List<String> to = Arrays.stream(message.getRecipients(Message.RecipientType.TO))
                    .map(address -> ((InternetAddress) address).getAddress())
                    .toList();

            return new RecordedMail(to, message.getSubject(), (String) message.getContent());
        } catch (MessagingException | IOException exception) {
            throw new MailSendException("無法解析測試信件", exception);
        }
    }
}
