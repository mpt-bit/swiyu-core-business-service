package ch.admin.bj.swiyu.core.business.modules.email.service;

import ch.admin.bj.swiyu.core.business.modules.email.domain.Email;
import ch.admin.bj.swiyu.core.business.modules.email.domain.HtmlToPlainTextConverter;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Hands a composed email to the SMTP gateway.
 *
 * <p>Sender, reply-to and recipients come from the {@link Email}, not from the local configuration:
 * the publishing side already decided them, and a stage that composed an email must not have it sent
 * under a different sender.
 *
 * <p>Since EID-6921 the body is an HTML document and the message goes out as multipart: a
 * {@code text/plain} part derived here, the {@code text/html} part as composed, and the logos as
 * inline images. Building both parts here rather than carrying them both over Kafka keeps the wire
 * format to one body field - the split into MIME parts is a property of the transport, not of the
 * notification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSendService {

    private static final String IMAGE_PATH = "email-images/";
    private static final String IMAGE_SUFFIX = ".png";
    private static final String CID_PREFIX = "cid:";

    /**
     * A content id must name a file and nothing else. The body arrives over Kafka, so it is not this
     * service's place to trust it with a path.
     */
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z0-9-]{1,64}");

    /** An {@code <img>} whose source is a {@code cid:} reference, with that reference captured. */
    private static final Pattern INLINE_IMAGE = Pattern.compile(
        "(?is)<img\\b[^>]*\\bsrc\\s*=\\s*[\"']" + CID_PREFIX + "([^\"']*)[\"'][^>]*>"
    );

    private final JavaMailSender mailSender;
    private final HtmlToPlainTextConverter htmlToPlainText;

    public void send(Email email) {
        log.info("Sending email {} to {} recipient(s)", email.emailType(), email.to().size());
        mailSender.send(toMimeMessage(email));
    }

    /**
     * {@link MimeMessage} rather than {@code SimpleMailMessage}: only this way can the encoding be set
     * explicitly, and only this way can a message carry more than one part.
     */
    private MimeMessage toMimeMessage(Email email) {
        var message = mailSender.createMimeMessage();
        var body = email.body();
        try {
            var multipart = isHtml(body);
            var helper = multipart
                ? new MimeMessageHelper(
                      message,
                      MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                      StandardCharsets.UTF_8.name()
                  )
                : new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(email.from());
            helper.setReplyTo(email.replyTo());
            helper.setTo(email.to().toArray(String[]::new));
            helper.setSubject(email.subject());
            if (multipart) {
                // Both parts first, inline images after - MimeMessageHelper needs the body of the
                // related part to exist before anything can be related to it.
                helper.setText(htmlToPlainText.convert(body), body);
                addInlineImages(helper, email, body);
            } else {
                helper.setText(body, false);
            }
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to compose the MIME message for the email", e);
        }
        return message;
    }

    /**
     * Decides from the body itself instead of trusting the name of the field it arrived in.
     *
     * <p>{@code TiSendEmailCommandPayload.plainTextMessage} carries the HTML document since EID-6921,
     * and renaming it would be a breaking schema change. A body that is not HTML - an older message
     * still on the topic, or a future publisher - is sent exactly the way it was before rather than
     * as markup a recipient would have to read by hand.
     */
    private static boolean isHtml(String body) {
        var start = body.stripLeading();
        return start.regionMatches(true, 0, "<!DOCTYPE", 0, 9) || start.regionMatches(true, 0, "<html", 0, 5);
    }

    /**
     * Attaches one inline image per {@code cid:} reference the body actually makes, so a template
     * that drops a logo does not keep shipping its bytes.
     */
    private static void addInlineImages(MimeMessageHelper helper, Email email, String body) throws MessagingException {
        for (var contentId : referencedContentIds(body)) {
            var resource = new ClassPathResource(IMAGE_PATH + contentId + IMAGE_SUFFIX);
            if (!resource.exists()) {
                // Not fatal: an email without its logo is worth more than no email at all.
                log.error(
                    "Email {} references the inline image '{}', which is not bundled with the service. " +
                        "Sending without it.",
                    email.emailType(),
                    contentId
                );
                continue;
            }
            helper.addInline(contentId, resource, MediaType.IMAGE_PNG_VALUE);
        }
    }

    private static Set<String> referencedContentIds(String body) {
        var contentIds = new LinkedHashSet<String>();
        var images = INLINE_IMAGE.matcher(body);
        while (images.find()) {
            var contentId = images.group(1);
            if (CONTENT_ID.matcher(contentId).matches()) {
                contentIds.add(contentId);
            } else {
                log.error("Ignoring inline image with a content id that is not a plain name: '{}'", contentId);
            }
        }
        return contentIds;
    }
}
