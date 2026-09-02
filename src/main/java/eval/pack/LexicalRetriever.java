package eval.pack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lexical RAG: score index/tags/id/heading, then expand YAML {@code related}.
 * No LLM. Body snippets are not indexed (Java examples leak across layers).
 */
public final class LexicalRetriever {

    public static final int MIN = 2;
    public static final int MAX = 4;

    private static final Set<String> STOP = Set.of(
            "на", "с", "и", "или", "для", "не", "ни", "но", "то", "от", "до", "из",
            "по", "со", "об", "за", "же", "бы", "ли", "это", "как", "что", "чем",
            "без", "через", "этот", "эта", "эти", "the", "a", "an", "of", "to", "in",
            "добавь", "покрой", "напиши", "автотест", "клики", "клик");
    private static final Set<String> LAYERS = Set.of("api", "e2e", "ui");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}@]+");
    private static final Pattern NOT_LAYER = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}])не\\s+(api|e2e|ui)(?![\\p{L}\\p{N}])");
    private static final List<String> STEMS = List.of(
            "ого", "его", "ами", "ями", "ыми", "ими", "ому", "ему", "ый", "ий", "ой",
            "ая", "ое", "ие", "ые", "ом", "ем", "ах", "ях", "ов", "ев", "ым", "им");

    private LexicalRetriever() {
    }

    public record Scored(String id, int score) {
    }

    public static List<String> retrieve(String query) {
        return retrieve(query, PackFiles.chunks());
    }

    public static List<String> retrieve(String query, List<RagChunk> corpus) {
        List<Scored> ranked = rank(query, corpus);
        if (ranked.isEmpty()) {
            return List.of();
        }
        Set<String> bannedLayers = bannedLayers(query);
        String seed = ranked.getFirst().id();
        LinkedHashSet<String> picked = new LinkedHashSet<>();
        picked.add(seed);
        RagChunk seedChunk = byId(corpus, seed);
        if (seedChunk != null) {
            for (String rel : seedChunk.related()) {
                if (picked.size() >= MAX) {
                    break;
                }
                if (byId(corpus, rel) != null) {
                    picked.add(rel);
                }
            }
        }
        for (Scored row : ranked) {
            if (picked.size() >= MIN) {
                break;
            }
            if (layerPenalized(byId(corpus, row.id()), bannedLayers)) {
                continue;
            }
            picked.add(row.id());
        }
        while (picked.size() > MAX) {
            picked.remove(picked.getLast());
        }
        return List.copyOf(picked);
    }

    public static List<Scored> rank(String query) {
        return rank(query, PackFiles.chunks());
    }

    public static List<Scored> rank(String query, List<RagChunk> corpus) {
        Set<String> q = tokens(query);
        Set<String> bannedLayers = bannedLayers(query);
        List<Scored> scored = new ArrayList<>();
        for (RagChunk chunk : corpus) {
            scored.add(new Scored(chunk.id(), score(q, bannedLayers, chunk)));
        }
        scored.sort(Comparator.comparingInt(Scored::score).reversed().thenComparing(Scored::id));
        return scored;
    }

    static int score(Set<String> query, Set<String> bannedLayers, RagChunk chunk) {
        int s = 0;
        s += 8 * overlap(query, tokens(chunk.id().replace('-', ' ')));
        s += 5 * overlap(query, tokens(String.join(" ", chunk.tags())));
        s += 4 * overlap(query, tokens(String.join(" ", chunk.index())));
        s += 2 * overlap(query, tokens(chunk.heading()));
        if (layerPenalized(chunk, bannedLayers)) {
            s -= 50;
        }
        return s;
    }

    static Set<String> tokens(String text) {
        Set<String> out = new HashSet<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String raw = m.group();
            if (STOP.contains(raw) || raw.length() < 2) {
                continue;
            }
            out.add(stem(raw));
        }
        return out;
    }

    static String stem(String token) {
        String s = token;
        if (s.endsWith("ь") && s.length() > 4) {
            s = s.substring(0, s.length() - 1);
        }
        for (String end : STEMS) {
            if (s.length() - end.length() >= 4 && s.endsWith(end)) {
                return s.substring(0, s.length() - end.length());
            }
        }
        return s;
    }

    static Set<String> bannedLayers(String query) {
        Set<String> banned = new HashSet<>();
        Matcher m = NOT_LAYER.matcher(query);
        while (m.find()) {
            banned.add(m.group(1).toLowerCase(Locale.ROOT));
        }
        return banned;
    }

    private static boolean layerPenalized(RagChunk chunk, Set<String> bannedLayers) {
        if (chunk == null || bannedLayers.isEmpty()) {
            return false;
        }
        Set<String> bag = tokens(
                chunk.id().replace('-', ' ')
                        + " "
                        + String.join(" ", chunk.tags())
                        + " "
                        + String.join(" ", chunk.index()));
        for (String layer : bannedLayers) {
            if (bag.contains(stem(layer))) {
                return true;
            }
        }
        return false;
    }

    private static int overlap(Set<String> query, Set<String> doc) {
        int n = 0;
        for (String t : query) {
            if (doc.contains(t)) {
                n++;
            }
        }
        return n;
    }

    private static RagChunk byId(List<RagChunk> corpus, String id) {
        for (RagChunk chunk : corpus) {
            if (chunk.id().equals(id)) {
                return chunk;
            }
        }
        return null;
    }
}
