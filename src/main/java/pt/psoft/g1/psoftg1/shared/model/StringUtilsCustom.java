package pt.psoft.g1.psoftg1.shared.model;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * Utility class for String operations.
 */
public final class StringUtilsCustom {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "b", "i", "u", "strong", "em", "br")
            .toFactory();

    private StringUtilsCustom() {
        // Prevent instantiation
    }

    /**
     * Sanitizes HTML content to prevent XSS attacks.
     *
     * @param html the HTML content to sanitize
     * @return the sanitized HTML content
     */
    public static String sanitizeHtml(String html) {
        if (html == null) {
            return null;
        }
        return POLICY.sanitize(html);
    }
}
