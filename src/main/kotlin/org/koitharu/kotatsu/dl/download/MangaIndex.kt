package org.koitharu.kotatsu.dl.download

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.model.*
import org.koitharu.kotatsu.parsers.util.find
import org.koitharu.kotatsu.parsers.util.json.*
import org.koitharu.kotatsu.parsers.util.toTitleCase

class MangaIndex(source: String?) {

    private val json: JSONObject = source?.let(::JSONObject) ?: JSONObject()

    fun setMangaInfo(manga: Manga) {
        json.put(KEY_ID, manga.id)
        json.put(KEY_TITLE, manga.title)
        json.put(KEY_TITLE_ALT, manga.altTitle) // for backward compatibility
        json.put(KEY_ALT_TITLES, JSONArray(manga.altTitles))
        json.put(KEY_URL, manga.url)
        json.put(KEY_PUBLIC_URL, manga.publicUrl)
        json.put(KEY_AUTHOR, manga.author) // for backward compatibility
        json.put(KEY_AUTHORS, JSONArray(manga.authors))
        json.put(KEY_COVER, manga.coverUrl)
        json.put(KEY_DESCRIPTION, manga.description)
        json.put(KEY_RATING, manga.rating)
        json.put(KEY_CONTENT_RATING, manga.contentRating)
        json.put(KEY_NSFW, manga.isNsfw) // for backward compatibility
        json.put(KEY_STATE, manga.state?.name)
        json.put(KEY_SOURCE, manga.source.name)
        json.put(KEY_COVER_LARGE, manga.largeCoverUrl)
        json.put(
            KEY_TAGS,
            JSONArray().also { a ->
                for (tag in manga.tags) {
                    val jo = JSONObject()
                    jo.put(KEY_KEY, tag.key)
                    jo.put(KEY_TITLE, tag.title)
                    a.put(jo)
                }
            },
        )
        if (!json.has(KEY_CHAPTERS)) {
            json.put(KEY_CHAPTERS, JSONObject())
        }
        json.put(KEY_APP_ID, "kotatsu-dl")
        json.put(KEY_APP_VERSION, "0.2")
    }

    fun getMangaInfo(): Manga? = if (json.length() == 0) null else runCatching {
        val source = requireNotNull(MangaParserSource.entries.find(json.getString(KEY_SOURCE))) {
            "Invalid manga source "
        }
        Manga(
            id = json.getLong(KEY_ID),
            title = json.getString(KEY_TITLE),
            altTitles = json.optJSONArray(KEY_ALT_TITLES)?.toStringSet()
                ?: setOfNotNull(json.getStringOrNull(KEY_TITLE_ALT)),
            url = json.getString(KEY_URL),
            publicUrl = json.getStringOrNull(KEY_PUBLIC_URL).orEmpty(),
            authors = json.optJSONArray(KEY_AUTHORS)?.toStringSet()
                ?: setOfNotNull(json.getStringOrNull(KEY_AUTHOR)),
            largeCoverUrl = json.getStringOrNull(KEY_COVER_LARGE),
            source = source,
            rating = json.getFloatOrDefault(KEY_RATING, RATING_UNKNOWN),
            contentRating = json.getEnumValueOrNull(KEY_CONTENT_RATING, ContentRating::class.java)
                ?: if (json.getBooleanOrDefault(KEY_NSFW, false)) ContentRating.ADULT else null,
            coverUrl = json.getStringOrNull(KEY_COVER),
            state = json.getEnumValueOrNull(KEY_STATE, MangaState::class.java),
            description = json.getStringOrNull(KEY_DESCRIPTION),
            tags = json.getJSONArray(KEY_TAGS).mapJSONToSet { x ->
                MangaTag(
                    title = x.getString(KEY_TITLE).toTitleCase(),
                    key = x.getString(KEY_KEY),
                    source = source,
                )
            },
            chapters = getChapters(json.getJSONObject(KEY_CHAPTERS), source),
        )
    }.getOrNull()

    fun getCoverEntry(): String? = json.getStringOrNull(KEY_COVER_ENTRY)

    fun addChapter(chapter: IndexedValue<MangaChapter>, filename: String?) {
        val chapters = json.getJSONObject(KEY_CHAPTERS)
        if (!chapters.has(chapter.value.id.toString())) {
            val jo = JSONObject()
            jo.put(KEY_NUMBER, chapter.value.number)
            jo.put(KEY_VOLUME, chapter.value.volume)
            jo.put(KEY_URL, chapter.value.url)
            jo.put(KEY_NAME, chapter.value.title.orEmpty())
            jo.put(KEY_UPLOAD_DATE, chapter.value.uploadDate)
            jo.put(KEY_SCANLATOR, chapter.value.scanlator)
            jo.put(KEY_BRANCH, chapter.value.branch)
            jo.put(KEY_ENTRIES, "%08d_%04d\\d{4}".format(chapter.value.branch.hashCode(), chapter.index + 1))
            jo.put(KEY_FILE, filename)
            chapters.put(chapter.value.id.toString(), jo)
        }
    }

    fun removeChapter(id: Long): Boolean {
        return json.has(KEY_CHAPTERS) && json.getJSONObject(KEY_CHAPTERS).remove(id.toString()) != null
    }

    fun getChapterFileName(chapterId: Long): String? {
        return json.optJSONObject(KEY_CHAPTERS)?.optJSONObject(chapterId.toString())?.getStringOrNull(KEY_FILE)
    }

    fun setCoverEntry(name: String) {
        json.put(KEY_COVER_ENTRY, name)
    }

    fun getChapterNamesPattern(chapter: MangaChapter) = Regex(
        json.getJSONObject(KEY_CHAPTERS)
            .getJSONObject(chapter.id.toString())
            .getString(KEY_ENTRIES),
    )

    fun clear() {
        val keys = json.keys()
        while (keys.hasNext()) {
            json.remove(keys.next())
        }
    }

    fun setFrom(other: MangaIndex) {
        clear()
        other.json.keys().forEach { key ->
            json.putOpt(key, other.json.opt(key))
        }
    }

    private fun getChapters(json: JSONObject, source: MangaSource): List<MangaChapter> {
        val chapters = ArrayList<MangaChapter>(json.length())
        for (k in json.keys()) {
            val v = json.getJSONObject(k)
            chapters.add(
                MangaChapter(
                    id = k.toLong(),
                    title = v.getStringOrNull(KEY_NAME),
                    url = v.getString(KEY_URL),
                    number = v.getFloatOrDefault(KEY_NUMBER, 0f),
                    volume = v.getIntOrDefault(KEY_VOLUME, 0),
                    uploadDate = v.getLongOrDefault(KEY_UPLOAD_DATE, 0L),
                    scanlator = v.getStringOrNull(KEY_SCANLATOR),
                    branch = v.getStringOrNull(KEY_BRANCH),
                    source = source,
                ),
            )
        }
        return chapters.sortedBy { it.number }
    }

    override fun toString(): String = json.toString(4)

    companion object {

        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_TITLE_ALT = "title_alt"
        private const val KEY_ALT_TITLES = "alt_titles"
        private const val KEY_URL = "url"
        private const val KEY_PUBLIC_URL = "public_url"
        private const val KEY_AUTHOR = "author"
        private const val KEY_AUTHORS = "authors"
        private const val KEY_COVER = "cover"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_RATING = "rating"
        private const val KEY_CONTENT_RATING = "content_rating"
        private const val KEY_NSFW = "nsfw"
        private const val KEY_STATE = "state"
        private const val KEY_SOURCE = "source"
        private const val KEY_COVER_LARGE = "cover_large"
        private const val KEY_TAGS = "tags"
        private const val KEY_CHAPTERS = "chapters"
        private const val KEY_NUMBER = "number"
        private const val KEY_VOLUME = "volume"
        private const val KEY_NAME = "name"
        private const val KEY_UPLOAD_DATE = "uploadDate"
        private const val KEY_SCANLATOR = "scanlator"
        private const val KEY_BRANCH = "branch"
        private const val KEY_ENTRIES = "entries"
        private const val KEY_FILE = "file"
        private const val KEY_COVER_ENTRY = "cover_entry"
        private const val KEY_KEY = "key"
        private const val KEY_APP_ID = "app_id"
        private const val KEY_APP_VERSION = "app_version"
    }
}