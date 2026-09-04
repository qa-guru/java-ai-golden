package eval.reporting;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small HTML wrapper for mill markdown (headings, tables, lists, code, bold, italic).
 * Not a general-purpose CommonMark implementation.
 */
public final class MarkdownHtml {

    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("_(.+?)_");

    private static final String CSS = """
            :root { color-scheme: light; }
            body { margin: 0; font-family: ui-sans-serif, system-ui, sans-serif; line-height: 1.5; color: #1f2328; background: #fff; }
            main { max-width: 52rem; margin: 0 auto; padding: 1.5rem 1.25rem 3rem; }
            .nav { max-width: 52rem; margin: 0 auto; padding: 1rem 1.25rem 0; }
            a { color: #0969da; }
            h1, h2, h3 { line-height: 1.25; }
            table { border-collapse: collapse; width: 100%; margin: 0.75rem 0 1.25rem; }
            th, td { border: 1px solid #d0d7de; padding: 0.35rem 0.6rem; text-align: left; vertical-align: top; }
            th { background: #f6f8fa; }
            code { font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 0.9em; background: #f6f8fa; padding: 0.1em 0.35em; border-radius: 4px; }
            ul { padding-left: 1.3rem; }
            .muted { color: #656d76; }
            .slots { list-style: none; padding: 0; }
            .slots li { border: 1px solid #d0d7de; border-radius: 8px; padding: 0.85rem 1rem; margin: 0.6rem 0; }
            """;

    private MarkdownHtml() {
    }

    public static String document(String title, String markdown, String homeHref) {
        String nav = "";
        if (homeHref != null && !homeHref.isBlank()) {
            nav = "<p class=\"nav\"><a href=\"" + escapeAttr(homeHref) + "\">← java-ai-golden eval</a></p>\n";
        }
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8"/>
                  <meta name="viewport" content="width=device-width, initial-scale=1"/>
                  <title>{{title}}</title>
                  <style>{{css}}</style>
                </head>
                <body>
                  {{nav}}<main class="markdown-body">
                {{body}}
                  </main>
                </body>
                </html>
                """
                .replace("{{title}}", escape(title))
                .replace("{{css}}", CSS)
                .replace("{{nav}}", nav)
                .replace("{{body}}", toHtml(markdown));
    }

    public static String toHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        String[] lines = markdown.split("\n", -1);
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            if (line.startsWith("```")) {
                i = fence(lines, i, out);
            } else if (line.startsWith("### ")) {
                out.append("<h3>").append(inline(line.substring(4))).append("</h3>\n");
                i++;
            } else if (line.startsWith("## ")) {
                out.append("<h2>").append(inline(line.substring(3))).append("</h2>\n");
                i++;
            } else if (line.startsWith("# ")) {
                out.append("<h1>").append(inline(line.substring(2))).append("</h1>\n");
                i++;
            } else if (line.startsWith("|")) {
                i = table(lines, i, out);
            } else if (isListItem(line)) {
                i = list(lines, i, out);
            } else if (line.isBlank()) {
                i++;
            } else {
                out.append("<p>").append(inline(line)).append("</p>\n");
                i++;
            }
        }
        return out.toString();
    }

    static String inline(String text) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            int tick = text.indexOf('`', i);
            if (tick < 0) {
                out.append(styled(escape(text.substring(i))));
                break;
            }
            out.append(styled(escape(text.substring(i, tick))));
            int end = text.indexOf('`', tick + 1);
            if (end < 0) {
                out.append(styled(escape(text.substring(tick))));
                break;
            }
            out.append("<code>").append(escape(text.substring(tick + 1, end))).append("</code>");
            i = end + 1;
        }
        return out.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String escapeAttr(String text) {
        return escape(text).replace("\"", "&quot;");
    }

    private static String styled(String escaped) {
        String withBold = replaceAll(BOLD, escaped, "<strong>", "</strong>");
        return replaceAll(ITALIC, withBold, "<em>", "</em>");
    }

    private static String replaceAll(Pattern pattern, String input, String open, String close) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement(open + m.group(1) + close));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isListItem(String line) {
        return line.startsWith("- ") || line.startsWith("  - ");
    }

    private static int list(String[] lines, int i, StringBuilder out) {
        out.append("<ul>\n");
        while (i < lines.length && isListItem(lines[i])) {
            String item = lines[i].startsWith("  - ") ? lines[i].substring(4) : lines[i].substring(2);
            out.append("<li>").append(inline(item)).append("</li>\n");
            i++;
        }
        out.append("</ul>\n");
        return i;
    }

    private static int table(String[] lines, int i, StringBuilder out) {
        out.append("<table>\n");
        boolean header = true;
        while (i < lines.length && lines[i].startsWith("|")) {
            if (isSeparator(lines[i])) {
                header = false;
                i++;
                continue;
            }
            String tag = header ? "th" : "td";
            out.append("<tr>");
            for (String cell : splitCells(lines[i])) {
                out.append('<').append(tag).append('>')
                        .append(inline(cell.trim()))
                        .append("</").append(tag).append('>');
            }
            out.append("</tr>\n");
            header = false;
            i++;
        }
        out.append("</table>\n");
        return i;
    }

    private static boolean isSeparator(String line) {
        for (String cell : splitCells(line)) {
            String t = cell.trim();
            if (t.isEmpty()) {
                continue;
            }
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c != '-' && c != ':') {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<String> splitCells(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String part : trimmed.split("\\|", -1)) {
            cells.add(part);
        }
        return cells;
    }

    private static int fence(String[] lines, int start, StringBuilder out) {
        int i = start + 1;
        StringBuilder body = new StringBuilder();
        while (i < lines.length && !lines[i].startsWith("```")) {
            if (!body.isEmpty()) {
                body.append('\n');
            }
            body.append(lines[i]);
            i++;
        }
        out.append("<pre><code>").append(escape(body.toString())).append("</code></pre>\n");
        return i < lines.length ? i + 1 : i;
    }
}
