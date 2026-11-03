package se.teaterihuskvarna.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.login.MailSettings;

/// Public discovery documents contain published pages and never member data or preview tokens.
@Controller
public class DiscoveryPageController {

    private final ContentService content;
    private final MailSettings mail;

    DiscoveryPageController(ContentService content, MailSettings mail) {
        this.content = content;
        this.mail = mail;
    }

    /// @return an XML sitemap of the published public pages
    @GetMapping(value = "/sitemap.xml", produces = "application/xml;charset=UTF-8")
    @ResponseBody
    public String sitemap() {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        for (String path : content.publicPaths()) {
            // With an encoding, only the five characters XML reserves are escaped. Without one, an å in
            // a slug typed into Sanity became &aring;, which XML does not define.
            xml.append("<url><loc>").append(HtmlUtils.htmlEscape(mail.link(path, null), "UTF-8"))
                    .append("</loc></url>\n");
        }
        return xml.append("</urlset>\n").toString();
    }

    /// @return crawler directions and the sitemap's configured address
    @GetMapping(value = "/robots.txt", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String robots() {
        return "User-agent: *\nDisallow: /admin\nDisallow: /medlem\nDisallow: /api/\nDisallow: /dev\n"
                + "Disallow: /forhandsgranska/\nDisallow: /logga-in\nDisallow: /inbjudan\n"
                + "Disallow: /bli-medlem/bekrafta\nDisallow: /bli-medlem/skickat\n"
                + "Sitemap: " + mail.link("/sitemap.xml", null) + "\n";
    }
}
