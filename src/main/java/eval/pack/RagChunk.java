package eval.pack;

import java.util.List;

public record RagChunk(
        String id,
        List<String> tags,
        List<String> related,
        List<String> index,
        String heading
) {
    public RagChunk {
        tags = tags == null ? List.of() : List.copyOf(tags);
        related = related == null ? List.of() : List.copyOf(related);
        index = index == null ? List.of() : List.copyOf(index);
        heading = heading == null ? "" : heading;
    }

    public RagChunk withIndex(List<String> newIndex) {
        return new RagChunk(id, tags, related, newIndex, heading);
    }
}
