package ch.admin.bj.swiyu.core.business.modules.email.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * Turns a rendered email into the {@code text/plain} alternative part of the multipart message.
 *
 * <p>Not a "strip the tags" helper: this is what recipients with a text-only client actually read,
 * and it is the rendering the reviewed plain text templates used to be before EID-6921 replaced them
 * with HTML. The wrapping column and the section rule below are theirs, kept so the text part still
 * looks the way partners have been receiving it.
 *
 * <p>Reads the document in a single pass instead of building a tree. That is enough because the only
 * input is our own rendered templates - well formed HTML5 from files that are reviewed before they
 * ship - and because only six tags carry meaning for the text version: {@code p}, {@code ul},
 * {@code ol}, {@code li}, {@code hr} and {@code a}. Everything else contributes its text and nothing
 * more. Fed arbitrary HTML from elsewhere it would disappoint; it is never asked to.
 *
 * <p>Lives next to the renderer today because publishing and sending share a service. It belongs to
 * the *sending* side though - when the Kafka-consuming dispatch is extracted into its own service,
 * this class moves there together with the logo assets, and the publisher keeps only the HTML.
 */
@Component
public class HtmlToPlainTextConverter {

    /**
     * Width the reviewed plain text templates were wrapped at, kept so the text part still looks the
     * way partners have been receiving it.
     */
    private static final int WRAP_COLUMN = 100;

    /**
     * Separator between the language sections, as many dashes as the reviewed templates used.
     */
    private static final String SECTION_RULE = "-".repeat(71);

    private static final String LIST_MARKER = "- ";
    private static final String LIST_INDENT = "  ";

    /** A link whose text is already the address; printing the destination would repeat it. */
    private static final String MAILTO_PREFIX = "mailto:";

    /**
     * Runs of whitespace, collapsed to a single space. Compiled once: it is applied to every block and
     * every link of every email.
     */
    private static final Pattern WHITESPACE_RUN = Pattern.compile("\\s+");

    /**
     * Elements whose content is not text for the reader.
     *
     * <p>One pattern per element rather than one with three alternatives: the combined expression is
     * harder to read than the three it replaces, and a backreference over the tag name would trade
     * that for a warning about super-linear runtime. Three plain patterns cost neither.
     */
    private static final List<Pattern> NON_TEXT_ELEMENTS = Stream.of("head", "style", "script")
        .map(tag -> Pattern.compile("(?is)<" + tag + "\\b[^>]*>.*?</" + tag + "\\s*>"))
        .toList();

    /** The destination of a link, quoted either way. */
    private static final Pattern HREF = Pattern.compile("(?i)\\bhref\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    public String convert(String html) {
        return String.join("\n\n", new Reader(html).blocks());
    }

    /**
     * Walks the document once, collecting one entry per block the reader should see as a paragraph.
     *
     * <p>Text accumulates in {@code current} until a tag closes a block. Containers - {@code
     * <section>}, the signature {@code <div>} - open and close without ever flushing, so their
     * children end up as the blocks instead of the container itself.
     */
    private static final class Reader {

        private final String input;
        private final List<String> blocks = new ArrayList<>();
        private final StringBuilder current = new StringBuilder();

        /** Items of the list currently open, empty when none is. */
        private final List<String> listItems = new ArrayList<>();
        private boolean inList;

        /** The destination of the link currently open, {@code null} when none is. */
        private String openHref;
        private int linkTextStart;

        Reader(String html) {
            var stripped = html == null ? "" : html;
            for (var nonText : NON_TEXT_ELEMENTS) {
                stripped = nonText.matcher(stripped).replaceAll("");
            }
            this.input = stripped;
        }

        List<String> blocks() {
            var position = 0;
            var atEnd = false;
            while (!atEnd && position < input.length()) {
                var tagStart = input.indexOf('<', position);
                var tagEnd = tagStart < 0 ? -1 : findTagEnd(tagStart);
                if (tagEnd < 0) {
                    // No tag left, or one that never closes: whatever remains is text for the reader.
                    current.append(decodeEntities(input.substring(position)));
                    atEnd = true;
                } else {
                    current.append(decodeEntities(input.substring(position, tagStart)));
                    handleTag(input.substring(tagStart + 1, tagEnd).strip());
                    position = tagEnd + 1;
                }
            }
            flushBlock();
            return blocks;
        }

        private void handleTag(String tag) {
            if (tag.startsWith("!") || tag.startsWith("?")) {
                return; // comment, doctype
            }
            var closing = tag.startsWith("/");
            var name = tagName(tag);
            switch (name) {
                // Both ends of a paragraph or a list item close what was collected: the opening tag
                // because anything before it was a block of its own, the closing one because the
                // block ends there.
                case "p", "li" -> flushBlock();
                case "ul", "ol" -> handleList(closing);
                case "hr" -> {
                    flushBlock();
                    blocks.add(SECTION_RULE);
                }
                case "br" -> current.append(' ');
                case "a" -> handleLink(tag, closing);
                default -> {
                    // <img> and every container contribute nothing of their own.
                }
            }
        }

        private void handleList(boolean closing) {
            flushBlock();
            inList = !closing;
            if (closing) {
                flushList();
            }
        }

        private void handleLink(String tag, boolean closing) {
            if (closing) {
                closeLink();
                return;
            }
            openHref = href(tag);
            linkTextStart = current.length();
        }

        /**
         * Renders the link that just closed: its text, plus the destination in parentheses so a text
         * reader still gets it. Two cases drop the parentheses - a {@code mailto:} link, whose text is
         * already the address, and a link whose text is its own URL - both would print the same string
         * twice.
         */
        private void closeLink() {
            if (openHref == null) {
                return;
            }
            var label = normalise(current.substring(linkTextStart));
            if (destinationAddsInformation(openHref, label)) {
                current.append(" (").append(openHref).append(')');
            }
            openHref = null;
        }

        /**
         * Whether printing the destination next to the label tells the reader anything new.
         *
         * <p>It does not for a {@code mailto:} link, whose label already is the address, nor for a
         * link whose label is its own URL - both would print the same string twice.
         */
        private static boolean destinationAddsInformation(String href, String label) {
            if (label.isEmpty() || href.isEmpty()) {
                return false;
            }
            return !href.startsWith(MAILTO_PREFIX) && !href.equals(label);
        }

        /**
         * Ends whatever has been collected: a line of a list while one is open, a block of its own
         * otherwise. Called at every boundary, which is why it tolerates having nothing to do.
         */
        private void flushBlock() {
            var text = normalise(current.toString());
            current.setLength(0);
            if (text.isEmpty()) {
                return;
            }
            if (inList) {
                listItems.add(wrap(LIST_MARKER + text, LIST_INDENT));
            } else {
                blocks.add(wrap(text, ""));
            }
        }

        /** A whole list is one block, its items one line each. */
        private void flushList() {
            if (!listItems.isEmpty()) {
                blocks.add(String.join("\n", listItems));
                listItems.clear();
            }
        }

        /** The element name, with the slash of a closing tag and any attributes stripped off. */
        private static String tagName(String tag) {
            var start = tag.startsWith("/") ? 1 : 0;
            var end = start;
            while (end < tag.length() && !Character.isWhitespace(tag.charAt(end)) && tag.charAt(end) != '/') {
                end++;
            }
            return tag.substring(start, end).toLowerCase(Locale.ROOT);
        }

        /** The destination of a link, or the empty string when the tag carries none. */
        private static String href(String tag) {
            var matcher = HREF.matcher(tag);
            if (!matcher.find()) {
                return "";
            }
            // Two capturing groups because the quote can be either kind; exactly one of them matched.
            return decodeEntities(Objects.requireNonNullElse(matcher.group(1), matcher.group(2)));
        }

        private static String normalise(String text) {
            return WHITESPACE_RUN.matcher(text).replaceAll(" ").strip();
        }

        /**
         * Greedy wrap at {@link #WRAP_COLUMN}. A word longer than the column gets its own line rather
         * than being broken - a URL must stay clickable.
         */
        private static String wrap(String text, String continuationIndent) {
            if (text.isEmpty()) {
                return text;
            }
            var wrapped = new StringBuilder();
            var lineLength = 0;
            for (var word : text.split(" ")) {
                if (lineLength == 0) {
                    wrapped.append(word);
                    lineLength = word.length();
                } else if (lineLength + 1 + word.length() <= WRAP_COLUMN) {
                    wrapped.append(' ').append(word);
                    lineLength += 1 + word.length();
                } else {
                    wrapped.append('\n').append(continuationIndent).append(word);
                    lineLength = continuationIndent.length() + word.length();
                }
            }
            return wrapped.toString();
        }

        /** The {@code >} that ends the tag, skipping any inside quoted attribute values. */
        private int findTagEnd(int from) {
            var quote = 0;
            for (var i = from + 1; i < input.length(); i++) {
                var c = input.charAt(i);
                if (quote != 0) {
                    if (c == quote) {
                        quote = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    quote = c;
                } else if (c == '>') {
                    return i;
                }
            }
            return -1;
        }
    }

    /**
     * The named references that can appear in a four language email: the five HTML ones plus the
     * accented characters of German, French and Italian. Anything beyond these arrives as UTF-8 or as
     * a numeric reference, both of which are handled without a table.
     */
    private static final Map<String, String> NAMED_ENTITIES = Map.ofEntries(
        Map.entry("amp", "&"),
        Map.entry("lt", "<"),
        Map.entry("gt", ">"),
        Map.entry("quot", "\""),
        Map.entry("apos", "'"),
        Map.entry("nbsp", "\u00A0"),
        Map.entry("auml", "ä"),
        Map.entry("Auml", "Ä"),
        Map.entry("ouml", "ö"),
        Map.entry("Ouml", "Ö"),
        Map.entry("uuml", "ü"),
        Map.entry("Uuml", "Ü"),
        Map.entry("szlig", "ß"),
        Map.entry("agrave", "à"),
        Map.entry("Agrave", "À"),
        Map.entry("aacute", "á"),
        Map.entry("acirc", "â"),
        Map.entry("egrave", "è"),
        Map.entry("Egrave", "È"),
        Map.entry("eacute", "é"),
        Map.entry("Eacute", "É"),
        Map.entry("ecirc", "ê"),
        Map.entry("euml", "ë"),
        Map.entry("igrave", "ì"),
        Map.entry("icirc", "î"),
        Map.entry("iuml", "ï"),
        Map.entry("ograve", "ò"),
        Map.entry("oacute", "ó"),
        Map.entry("ocirc", "ô"),
        Map.entry("ugrave", "ù"),
        Map.entry("ucirc", "û"),
        Map.entry("ccedil", "ç"),
        Map.entry("Ccedil", "Ç")
    );

    /**
     * Decodes the entities Thymeleaf's HTML escaping produces. An entity we do not know is left as it
     * stands - visible in the output, which is easier to spot and fix than a dropped character.
     */
    static String decodeEntities(String raw) {
        if (raw.indexOf('&') < 0) {
            return raw;
        }
        var decoded = new StringBuilder(raw.length());
        var i = 0;
        while (i < raw.length()) {
            var c = raw.charAt(i);
            var semicolon = c == '&' ? raw.indexOf(';', i + 1) : -1;
            var replacement =
                semicolon > 0 && semicolon - i <= 12 ? resolveEntity(raw.substring(i + 1, semicolon)) : null;
            if (replacement == null) {
                decoded.append(c);
                i++;
            } else {
                decoded.append(replacement);
                i = semicolon + 1;
            }
        }
        return decoded.toString();
    }

    private static String resolveEntity(String body) {
        if (!body.startsWith("#")) {
            return NAMED_ENTITIES.get(body);
        }
        try {
            var codePoint =
                body.startsWith("#x") || body.startsWith("#X")
                    ? Integer.parseInt(body.substring(2), 16)
                    : Integer.parseInt(body.substring(1));
            return Character.toString(codePoint);
        } catch (IllegalArgumentException _) {
            // Covers both a body that is not a number and a number that is not a code point.
            return null;
        }
    }
}
