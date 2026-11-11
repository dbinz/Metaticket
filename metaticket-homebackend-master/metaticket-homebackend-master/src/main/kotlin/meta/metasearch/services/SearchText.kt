package meta.metasearch.services

import org.jsoup.Jsoup

/** Derives search-safe plain text from admin-authored HTML (e.g. `Show.description`). */
object SearchText {

    /**
     * Strips tags/CSS (Jsoup drops `<style>`/`<script>` bodies, unlike a tag-stripping regex which
     * would leave their contents behind) and truncates at a word boundary so the index stays small.
     */
    fun plain(html: String?, maxChars: Int): String? {
        if (html.isNullOrBlank()) return null
        val text = Jsoup.parse(html).text().trim()
        if (text.isEmpty()) return null
        if (text.length <= maxChars) return text
        val cut = text.lastIndexOf(' ', maxChars)
        return if (cut > 0) text.substring(0, cut) else text.substring(0, maxChars)
    }
}
