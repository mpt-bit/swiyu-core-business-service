package ch.admin.bj.swiyu.core.business.modules.email.domain;

/**
 * A fully composed email: the subject line carrying all four languages, and the body as an HTML
 * document with the DE / FR / IT / EN sections.
 *
 * <p>Named {@code body} and not {@code htmlMessage}: this is the one rendering of the mail that the
 * publisher produces. The {@code text/plain} alternative part is derived from it in
 * {@code EmailSendService}, where the MIME message is assembled - see EID-6921.
 */
public record RenderedEmail(String subject, String body) {}
