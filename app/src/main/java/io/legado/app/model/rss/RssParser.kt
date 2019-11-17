package io.legado.app.model.rss

import io.legado.app.constant.RSSKeywords
import io.legado.app.data.entities.RssArticle
import io.legado.app.model.Debug
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

object RssParser {

    @Throws(XmlPullParserException::class, IOException::class)
    fun parseXML(xml: String, sourceUrl: String): MutableList<RssArticle> {

        val articleList = mutableListOf<RssArticle>()
        var currentArticle = RssArticle()

        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = false

        val xmlPullParser = factory.newPullParser()
        xmlPullParser.setInput(StringReader(xml))

        // A flag just to be sure of the correct parsing
        var insideItem = false

        var eventType = xmlPullParser.eventType

        // Start parsing the xml
        loop@ while (eventType != XmlPullParser.END_DOCUMENT) {

            // Start parsing the item
            if (eventType == XmlPullParser.START_TAG) {
                when {
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM, true) ->
                        insideItem = true
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_TITLE, true) ->
                        if (insideItem) currentArticle.title = xmlPullParser.nextText().trim()
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_LINK, true) ->
                        if (insideItem) currentArticle.link = xmlPullParser.nextText().trim()
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_CATEGORY, true) ->
                        if (insideItem) currentArticle.categoryList.add(xmlPullParser.nextText().trim())
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_THUMBNAIL, true) ->
                        if (insideItem) currentArticle.image =
                            xmlPullParser.getAttributeValue(null, RSSKeywords.RSS_ITEM_URL)
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_ENCLOSURE, true) ->
                        if (insideItem) {
                            val type =
                                xmlPullParser.getAttributeValue(null, RSSKeywords.RSS_ITEM_TYPE)
                            if (type != null && type.contains("image/")) {
                                currentArticle.image =
                                    xmlPullParser.getAttributeValue(null, RSSKeywords.RSS_ITEM_URL)
                            }
                        }
                    xmlPullParser.name
                        .equals(RSSKeywords.RSS_ITEM_DESCRIPTION, true) ->
                        if (insideItem) {
                            val description = xmlPullParser.nextText()
                            currentArticle.description = description.trim()
                            if (currentArticle.image == null) {
                                currentArticle.image = getImageUrl(description)
                            }
                        }
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_CONTENT, true) ->
                        if (insideItem) {
                            val content = xmlPullParser.nextText().trim()
                            currentArticle.content = content
                            if (currentArticle.image == null) {
                                currentArticle.image = getImageUrl(content)
                            }
                        }
                    xmlPullParser.name
                        .equals(RSSKeywords.RSS_ITEM_PUB_DATE, true) ->
                        if (insideItem) {
                            val nextTokenType = xmlPullParser.next()
                            if (nextTokenType == XmlPullParser.TEXT) {
                                currentArticle.pubDate = xmlPullParser.text.trim()
                            }
                            // Skip to be able to find date inside 'tag' tag
                            continue@loop
                        }
                    xmlPullParser.name.equals(RSSKeywords.RSS_ITEM_TIME, true) ->
                        if (insideItem) currentArticle.pubDate = xmlPullParser.nextText()
                }
            } else if (eventType == XmlPullParser.END_TAG
                && xmlPullParser.name.equals("item", true)
            ) {
                // The item is correctly parsed
                insideItem = false
                currentArticle.categories = currentArticle.categoryList.joinToString(",")
                currentArticle.origin = sourceUrl
                articleList.add(currentArticle)
                currentArticle = RssArticle()
            }
            eventType = xmlPullParser.next()
        }
        articleList.reverse()
        for ((index: Int, item: RssArticle) in articleList.withIndex()) {
            item.order = System.currentTimeMillis() + index
            if (index == 0) {
                Debug.log(sourceUrl, "┌获取标题")
                Debug.log(sourceUrl, "└${item.title}")
                Debug.log(sourceUrl, "┌获取时间")
                Debug.log(sourceUrl, "└${item.pubDate}")
                Debug.log(sourceUrl, "┌获取类别")
                Debug.log(sourceUrl, "└${item.categories}")
                Debug.log(sourceUrl, "┌获取描述")
                Debug.log(sourceUrl, "└${item.description}")
                Debug.log(sourceUrl, "┌获取图片url")
                Debug.log(sourceUrl, "└${item.image}")
                Debug.log(sourceUrl, "┌获取文章链接")
                Debug.log(sourceUrl, "└${item.link}")
            }
        }
        return articleList
    }

    /**
     * Finds the first img tag and get the src as featured image
     *
     * @param input The content in which to search for the tag
     * @return The url, if there is one
     */
    private fun getImageUrl(input: String): String? {

        var url: String? = null
        val patternImg = "(<img .*?>)".toPattern()
        val matcherImg = patternImg.matcher(input)
        if (matcherImg.find()) {
            val imgTag = matcherImg.group(1)
            val patternLink = "src\\s*=\\s*\"(.+?)\"".toPattern()
            val matcherLink = patternLink.matcher(imgTag)
            if (matcherLink.find()) {
                url = matcherLink.group(1).trim()
            }
        }
        return url
    }
}