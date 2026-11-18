package com.pulse.music.ui.search

import com.pulse.music.domain.Song
import java.text.Normalizer

/**
 * On-device relevance for search.
 *
 * The server returns whatever the music provider matched on the raw text. People type
 * "kinng shit shubh", "play baller", "shubh king shit lyrics": mixed order, typos, filler
 * words, song + singer together. This cleans the query, scores every result against it
 * (title, artist, album, typo-tolerant) and proposes sub-queries for when results look weak.
 */
object SearchRanker {

    private val filler = setOf(
        "play", "please", "song", "songs", "lyrics", "lyric", "video", "official", "audio",
        "mp3", "full", "hd", "by", "feat", "ft", "download", "new", "latest", "gana", "gaana"
    )

    /** Lower-case, strip accents and punctuation. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /** Meaningful words of a query (filler removed unless it would leave nothing). */
    fun tokens(query: String): List<String> {
        val all = normalize(query).split(' ').filter { it.isNotBlank() }
        val meaningful = all.filter { it !in filler }
        return meaningful.ifEmpty { all }
    }

    private fun edits(a: String, b: String, max: Int): Int {
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            var rowMin = cur[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (cur[j] < rowMin) rowMin = cur[j]
            }
            if (rowMin > max) return max + 1
            prev = cur
        }
        return prev[b.length]
    }

    /** How well one query word matches any of the candidate's words (0..1). */
    private fun wordScore(q: String, candidate: List<String>, joined: String): Double {
        var best = 0.0
        val maxEdits = when {
            q.length >= 8 -> 2
            q.length >= 4 -> 1
            else -> 0
        }
        for (c in candidate) {
            val s = when {
                c == q -> 1.0
                q.length >= 2 && c.startsWith(q) -> 0.9
                c.length >= 3 && q.startsWith(c) -> 0.7
                q.length >= 3 && c.contains(q) -> 0.65
                maxEdits > 0 -> {
                    val e = edits(q, c, maxEdits)
                    if (e <= maxEdits) 0.85 - 0.1 * e else 0.0
                }
                else -> 0.0
            }
            if (s > best) best = s
        }
        // "kingshit" typed as one word, or words glued together
        if (best < 0.7 && q.length >= 4 && joined.contains(q)) best = 0.7
        return best
    }

    fun score(song: Song, query: String): Double {
        val q = tokens(query)
        if (q.isEmpty()) return 0.0
        val title = normalize(song.title).split(' ').filter { it.isNotBlank() }
        val artist = normalize(song.artist).split(' ').filter { it.isNotBlank() }
        val album = normalize(song.album ?: "").split(' ').filter { it.isNotBlank() }
        val titleJoined = title.joinToString("")
        val artistJoined = artist.joinToString("")

        var sum = 0.0
        var minBest = 1.0
        for (word in q) {
            val best = maxOf(
                wordScore(word, title, titleJoined),
                wordScore(word, artist, artistJoined) * 0.9,
                wordScore(word, album, "") * 0.5
            )
            sum += best
            if (best < minBest) minBest = best
        }
        var total = sum / q.size

        val cleanQuery = q.joinToString(" ")
        val cleanTitle = title.joinToString(" ")
        if (cleanTitle == cleanQuery) total += 0.5
        else if (cleanTitle.startsWith(cleanQuery)) total += 0.25
        if (minBest >= 0.6) total += 0.15 // every word explained by title/artist
        return total
    }

    /** Best matches first; the provider's own order breaks ties. */
    fun rank(songs: List<Song>, query: String): List<Song> =
        songs.withIndex()
            .sortedWith(compareByDescending<IndexedValue<Song>> { score(it.value, query) }.thenBy { it.index })
            .map { it.value }

    /** Four or more words is more likely a sung line than a song title or an artist. */
    fun looksLikeLyrics(query: String): Boolean = tokens(query).size >= 4

    /** Score of the best result, to decide whether the results look weak. */
    fun bestScore(songs: List<Song>, query: String): Double =
        songs.maxOfOrNull { score(it, query) } ?: 0.0

    /**
     * Extra queries worth trying when the full text found little: "king shit shubh" also
     * tries "king shit" and "shit shubh" so a song + singer combination still hits.
     */
    fun variants(query: String): List<String> {
        val t = tokens(query)
        if (t.size < 2) return emptyList()
        val original = t.joinToString(" ")
        val out = linkedSetOf<String>()
        if (t.size >= 3) {
            out += t.dropLast(1).joinToString(" ")
            out += t.drop(1).joinToString(" ")
        } else {
            out += t[0]
            out += t[1]
        }
        out.remove(original)
        return out.take(3)
    }
}
