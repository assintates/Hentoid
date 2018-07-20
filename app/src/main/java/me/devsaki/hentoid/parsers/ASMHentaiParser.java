package me.devsaki.hentoid.parsers;

import android.webkit.URLUtil;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.Site;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import timber.log.Timber;

/**
 * Created by avluis on 07/24/2016.
 * Handles parsing of content from asmhentai.com
 */
public class ASMHentaiParser {

    public static Content parseContent(String urlString, Site site) throws IOException {
        Document doc = Jsoup.connect(urlString).get();

        Elements content = doc.select("div.info");
        if (content.size() > 0) {
            String url = doc.select("div.cover")
                    .select("a")
                    .attr("href")
                    .replace("/gallery", "");
            url = url.substring(0, url.length() - 2);

            String coverUrl = "http:"
                    + doc.select("div.cover")
                    .select("a")
                    .select("img")
                    .attr("src");

            String title = doc.select("div.info")
                    .select("h1")
                    .first()
                    .text();

            int pages = Integer.parseInt(doc.select("div.pages")
                    .get(0)
                    .select("h3")
                    .text()
                    .replace("Pages: ", ""));

            AttributeMap attributes = new AttributeMap();

            Elements artistElements = content
                    .select("div.tags:contains(Artists)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.ARTIST, artistElements);

            Elements tagElements = content
                    .select("div.tags:contains(Tags)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.TAG, tagElements);

            Elements seriesElements = content
                    .select("div.tags:contains(Parody)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.SERIE, seriesElements);

            Elements characterElements = content
                    .select("div.tags:contains(Characters)")
                    .select("div.tag_list")
                    .select("a");
            parseAttributes(attributes, AttributeType.CHARACTER, characterElements);

            String author = "";
            if (attributes.containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0)
                author = attributes.get(AttributeType.ARTIST).get(0).getName();

            return new Content()
                    .setTitle(title)
                    .setAuthor(author)
                    .setUrl(url)
                    .setCoverImageUrl(coverUrl)
                    .setAttributes(attributes)
                    .setQtyPages(pages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(site);
        }

        return null;
    }

    private static void parseAttributes(AttributeMap map, AttributeType type, Elements elements) {
        for (Element a : elements) {
            Attribute attribute = new Attribute();
            attribute.setType(type);
            attribute.setUrl(a.attr("href"));

            String name = a.text();
            // Remove counters from ASMhentai metadata (e.g. "Futanari (2660)" => "Futanari")
            int bracketPos = name.lastIndexOf("(");
            if (bracketPos > 1 && ' ' == name.charAt(bracketPos - 1)) bracketPos--;
            if (bracketPos > -1) name = name.substring(0, bracketPos);
            attribute.setName(name);

            map.add(attribute);
        }
    }

    public static List<String> parseImageList(Content content) {
        int pages = content.getQtyPages();
        String readerUrl = content.getReaderUrl();
        List<String> imgUrls = new ArrayList<>();

        if (!URLUtil.isValidUrl(readerUrl)) {
            Timber.e("Invalid URL : %s", readerUrl);
            return imgUrls;
        }

        Document doc;
        try {
            doc = Jsoup.connect(readerUrl).get();
            String imgUrl = "http:" +
                    doc.select("div.full_gallery")
                            .select("a")
                            .select("img")
                            .attr("src");
            // TODO: Verify extension types on this source
            String ext = imgUrl.substring(imgUrl.lastIndexOf('.'));

            for (int i = 0; i < pages; i++) {
                String img = imgUrl.substring(0, imgUrl.lastIndexOf('/') + 1) + (i + 1) + ext;
                imgUrls.add(img);
            }

        } catch (IOException e) {
            Timber.e(e, "Error while attempting to connect to: %s", readerUrl);
        }
        Timber.d("%s", imgUrls);

        return imgUrls;
    }
}
