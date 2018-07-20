package me.devsaki.hentoid.parsers;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.devsaki.hentoid.database.domains.Attribute;
import me.devsaki.hentoid.database.domains.Content;
import me.devsaki.hentoid.enums.AttributeType;
import me.devsaki.hentoid.enums.StatusContent;
import me.devsaki.hentoid.util.AttributeMap;
import me.devsaki.hentoid.util.HttpClientHelper;
import timber.log.Timber;

import static me.devsaki.hentoid.enums.Site.HITOMI;

/**
 * Created by neko on 08/07/2015.
 * Handles parsing of content from hitomi.la
 */
public class HitomiParser {

    // Reproduction of the Hitomi.la Javascript to find the hostname of the image server (see subdomain_from_url@reader.js)
    private final static int NUMBER_OF_FRONTENDS = 2;
    private final static String HOSTNAME_SUFFIX = "a";
    private final static char HOSTNAME_PREFIX_BASE = 97;

    public static Content parseContent(String urlString) throws IOException {
        Document doc = Jsoup.connect(urlString).get();
        Elements content = doc.select(".content");
        if (content.size() > 0) {
            String coverImageUrl = "https:" + content.select(".cover img").attr("src");
            Element info = content.select(".gallery").first();
            Element titleElement = info.select("h1").first();
            String url = titleElement.select("a").first().attr("href").replace("/reader", "");
            String title = titleElement.text();

            AttributeMap attributes = new AttributeMap();
            parseAttributes(attributes, AttributeType.ARTIST, info.select("h2").select("a"));

            Elements rows = info.select("tr");

            for (Element element : rows) {
                Element td = element.select("td").first();
                if (td.html().startsWith("Group")) {
                    parseAttributes(attributes, AttributeType.CIRCLE, element.select("a"));
                } else if (td.html().startsWith("Series")) {
                    parseAttributes(attributes, AttributeType.SERIE, element.select("a"));
                } else if (td.html().startsWith("Character")) {
                    parseAttributes(attributes, AttributeType.CHARACTER, element.select("a"));
                } else if (td.html().startsWith("Tags")) {
                    parseAttributes(attributes, AttributeType.TAG, element.select("a"));
                } else if (td.html().startsWith("Language")) {
                    parseAttributes(attributes, AttributeType.LANGUAGE, element.select("a"));
                } else if (td.html().startsWith("Type")) {
                    parseAttributes(attributes, AttributeType.CATEGORY, element.select("a"));
                }
            }
            int pages = doc.select(".thumbnail-container").size();

            String author = "";
            if (attributes.containsKey(AttributeType.ARTIST) && attributes.get(AttributeType.ARTIST).size() > 0)
                author = attributes.get(AttributeType.ARTIST).get(0).getName();
            if (author.equals("")) // Try and get Circle
            {
                if (attributes.containsKey(AttributeType.CIRCLE) && attributes.get(AttributeType.CIRCLE).size() > 0)
                    author = attributes.get(AttributeType.CIRCLE).get(0).getName();
            }

            return new Content()
                    .setTitle(title)
                    .setAuthor(author)
                    .setUrl(url)
                    .setCoverImageUrl(coverImageUrl)
                    .setAttributes(attributes)
                    .setQtyPages(pages)
                    .setStatus(StatusContent.SAVED)
                    .setSite(HITOMI);
        }

        return null;
    }

    private static void parseAttributes(AttributeMap map, AttributeType type, Elements elements) {
        for (Element a : elements) {
            map.add(new Attribute()
                    .setType(type)
                    .setUrl(a.attr("href"))
                    .setName(a.text()));
        }
    }

    public static List<String> parseImageList(Content content) {
        String html;
        List<String> imgUrls = Collections.emptyList();
        try {
            String url = content.getReaderUrl();
            html = HttpClientHelper.call(url);
            Timber.d("Parsing: %s", url);
            Document doc = Jsoup.parse(html);
            Elements imgElements = doc.select(".img-url");
            imgUrls = new ArrayList<>(imgElements.size());
            // New Hitomi image URLs starting from mid-april 2018
            //  If book ID is even or < 4, starts with 'aa'; else starts with 'ba'
            int referenceId = Integer.parseInt(content.getUniqueSiteId()) % 10;
            if (1 == referenceId || 3 == referenceId) referenceId = 0; // Yes, this is what Hitomi actually does (see common.js)
            String imageHostname = Character.toString((char) (HOSTNAME_PREFIX_BASE + referenceId % NUMBER_OF_FRONTENDS)) + HOSTNAME_SUFFIX;

            for (Element element : imgElements) {
                imgUrls.add("https:" + element.text().replace("//g.", "//" + imageHostname + "."));
            }
        } catch (Exception e) {
            Timber.e(e, "Could not connect to the requested resource");
        }
        Timber.d("%s", imgUrls);

        return imgUrls;
    }
}
