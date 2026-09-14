package ch.admin.bj.swiyu.core.business.modules.email.domain;

import static ch.admin.bj.swiyu.core.business.modules.email.domain.EmailTemplateFixture.variablesFor;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class EmailContentRendererTest {

    /**
     * The two variables every template uses. The renewal reminder's third one is added by
     * {@link EmailTemplateFixture#variablesFor}; the tests below that pin the subject line need no
     * variables beyond these.
     */
    private static final Map<String, Object> COMMON_VARIABLES = variablesFor(EmailType.SUBMISSION_ACCEPTED);

    private EmailContentRenderer renderer;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        renderer = EmailTemplateFixture.renderer();
        logAppender = new ListAppender<>();
        logAppender.start();
        logger = (Logger) LoggerFactory.getLogger(EmailContentRenderer.class);
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EmailType.class)
    void rendersEveryTemplateWithoutLoggingAnError(EmailType emailType) {
        var email = renderer.render(emailType, variablesFor(emailType), "");

        assertThat(email.subject()).isNotBlank();
        assertThat(email.body()).isNotBlank();
        assertThat(errorMessages()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EmailType.class)
    void everyTemplateDeclaresOnlyKnownVariables(EmailType emailType) {
        assertThat(renderer.declaredVariables(emailType)).containsExactlyInAnyOrderElementsOf(
            variablesFor(emailType).keySet()
        );
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(EmailType.class)
    void everyTemplateHasAllFourLanguageSections(EmailType emailType) {
        var email = renderer.render(emailType, variablesFor(emailType), "");

        // Body order per the feature specification: DE, FR, IT, EN
        assertThat(email.body()).containsSubsequence("Guten Tag", "Bonjour", "Buongiorno", "Hello");
    }

    @Test
    void prependsTheStageMarkerToTheSubject() {
        var email = renderer.render(EmailType.SUBMISSION_ACCEPTED, COMMON_VARIABLES, "[REF]");

        assertThat(email.subject()).isEqualTo(
            "[REF] Antrag eingereicht/ Application submitted/ Demande déposée/ Richiesta presentata"
        );
    }

    @Test
    void leavesTheSubjectUnchangedOnProdWhereNoPrefixIsConfigured() {
        var email = renderer.render(EmailType.SUBMISSION_ACCEPTED, COMMON_VARIABLES, "");

        assertThat(email.subject()).isEqualTo(
            "Antrag eingereicht/ Application submitted/ Demande déposée/ Richiesta presentata"
        );
    }

    @Test
    void doesNotDuplicateTheSpaceOfAPrefixConfiguredWithOne() {
        var email = renderer.render(EmailType.SUBMISSION_ACCEPTED, COMMON_VARIABLES, "[ABN-PREVIEW] ");

        assertThat(email.subject()).startsWith("[ABN-PREVIEW] Antrag");
    }

    @Test
    void rendersTheBodyAsAnHtmlDocument() {
        var email = renderer.render(EmailType.SUBMISSION_ACCEPTED, COMMON_VARIABLES, "");

        // Stripped: the template file ends with a newline and Thymeleaf carries it through, which
        // says nothing about the document being complete.
        assertThat(email.body().strip())
            .startsWith("<!DOCTYPE html>")
            .contains("<section lang=\"de\">")
            .endsWith("</html>");
    }

    @Test
    void pullsInTheSignatureFragmentInEveryLanguage() {
        var email = renderer.render(EmailType.SUBMISSION_ACCEPTED, COMMON_VARIABLES, "");

        // A th:replace that failed to resolve leaves the placeholder div behind rather than failing,
        // so the closings are what proves the four fragments were actually pulled in.
        assertThat(email.body()).contains("Freundliche Grüsse", "Cordialement", "Cordiali saluti", "Kind regards");
        assertThat(email.body()).doesNotContain("th:replace");
    }

    @Test
    void escapesResolvedVariablesInsteadOfInjectingThemAsMarkup() {
        var variables = new HashMap<>(COMMON_VARIABLES);
        variables.put("contactEmail", "<script>alert(1)</script>@example.com");

        var email = renderer.render(EmailType.SUBMISSION_ACCEPTED, variables, "");

        // The templates use th:text and [[${...}]], never the unescaped [(${...})] form. A value from
        // configuration is unlikely to be hostile, but the escaping rule must not depend on that.
        assertThat(email.body()).doesNotContain("<script>").contains("&lt;script&gt;");
    }

    @Test
    void logsAnErrorWhenAVariableIsNotSupplied() {
        renderer.render(EmailType.TRUST_REGISTRATION_REJECTED, Map.of("contactEmail", "a@b.ch"), "");

        assertThat(errorMessages()).anyMatch(message -> message.contains("servicePortalPartnerUrl"));
    }

    private List<String> errorMessages() {
        return logAppender.list
            .stream()
            .filter(event -> event.getLevel() == Level.ERROR)
            .map(ILoggingEvent::getFormattedMessage)
            .toList();
    }
}
