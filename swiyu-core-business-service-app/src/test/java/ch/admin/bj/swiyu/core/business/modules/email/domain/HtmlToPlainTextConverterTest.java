package ch.admin.bj.swiyu.core.business.modules.email.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The rules of the {@code text/plain} alternative part, one by one.
 *
 * <p>Each case states why the rule exists, and together they cover shapes the 15 templates happen not
 * to contain today - an ordered list, a link whose text is not its URL, a word longer than the wrap
 * column. Since the reviewed plain text baselines were dropped, this is the only test that pins the
 * converter's output at all, so a rule removed here is a rule nothing checks any more.
 */
class HtmlToPlainTextConverterTest {

    private final HtmlToPlainTextConverter converter = new HtmlToPlainTextConverter();

    @Test
    void separatesParagraphsByABlankLine() {
        assertThat(convert("<p>One</p><p>Two</p>")).isEqualTo("One\n\nTwo");
    }

    @Test
    void joinsTheSoftWrappedLinesOfAParagraph() {
        // The source wraps for readability; those breaks are layout and must not survive.
        assertThat(convert("<p>One\n   two\n   three</p>")).isEqualTo("One two three");
    }

    @Test
    void rendersAListAsOneBlockOfDashPrefixedLines() {
        assertThat(convert("<ul><li>First</li><li>Second</li></ul>")).isEqualTo("- First\n- Second");
    }

    @Test
    void printsTheUrlOfALinkSoATextOnlyReaderCanStillReachIt() {
        assertThat(convert("<p>See <a href=\"https://example.ch/x\">the portal</a></p>")).isEqualTo(
            "See the portal (https://example.ch/x)"
        );
    }

    @Test
    void doesNotRepeatAUrlThatIsAlreadyTheLinkText() {
        assertThat(convert("<p><a href=\"https://example.ch\">https://example.ch</a></p>")).isEqualTo(
            "https://example.ch"
        );
    }

    @Test
    void printsOnlyTheAddressOfAMailtoLink() {
        assertThat(convert("<p>Write to <a href=\"mailto:a@b.ch\">a@b.ch</a>.</p>")).isEqualTo("Write to a@b.ch.");
    }

    @Test
    void dropsImagesAndTheEmptyBlocksTheyLeaveBehind() {
        // The signature logo is an image inside a link inside its own paragraph. None of it carries
        // information the address block does not already state, and an empty block would show up as
        // a stray blank line.
        assertThat(
            convert("<p>Text</p><p><a href=\"https://swiyu.ch\"><img src=\"cid:logo\" alt=\"swiyu\"></a></p>")
        ).isEqualTo("Text");
    }

    @Test
    void turnsTheSectionRuleIntoTheDashLineTheReviewedTemplatesUsed() {
        assertThat(convert("<section><p>De</p></section><hr><section><p>Fr</p></section>")).isEqualTo(
            "De\n\n" + "-".repeat(71) + "\n\nFr"
        );
    }

    @Test
    void resolvesCharacterReferencesTheWayAMailClientWould() {
        assertThat(convert("<p>Gr&uuml;sse &amp; Dank</p>")).isEqualTo("Grüsse & Dank");
    }

    @Test
    void wrapsLongTextAndHangsTheContinuationOfAListItemUnderItsText() {
        var item = "word ".repeat(30).strip();

        var converted = convert("<ul><li>" + item + "</li></ul>");

        assertThat(converted.lines()).allSatisfy(line -> assertThat(line).hasSizeLessThanOrEqualTo(100));
        assertThat(converted).startsWith("- word").contains("\n  word");
    }

    @Test
    void keepsALongUrlOnOneLineSoItStaysClickable() {
        var url = "https://portal.trust-infra.swiyu.admin.ch/ui/business-partners/" + "0".repeat(120);

        assertThat(convert("<p>Link: <a href=\"" + url + "\">" + url + "</a></p>")).isEqualTo("Link:\n" + url);
    }

    @Test
    void ignoresTheHeadSoTheSubjectDoesNotAppearInTheBody() {
        assertThat(convert("<html><head><title>Subject</title></head><body><p>Body</p></body></html>")).isEqualTo(
            "Body"
        );
    }

    private String convert(String html) {
        return converter.convert(html);
    }
}
