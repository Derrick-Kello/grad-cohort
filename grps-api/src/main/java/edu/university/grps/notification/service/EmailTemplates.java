package edu.university.grps.notification.service;

import org.springframework.web.util.HtmlUtils;

/** Branded HTML email with a plain-text fallback (FR-NTF-02). */
public final class EmailTemplates {

    private EmailTemplates() {}

    public static String html(String heading, String body, String actionUrl, String actionLabel) {
        String button = actionUrl == null ? "" : """
                <p style="margin:24px 0 0"><a href="%s" style="background:#1f4e79;color:#ffffff;padding:10px 18px;
                border-radius:6px;text-decoration:none;font-weight:600">%s</a></p>"""
                .formatted(HtmlUtils.htmlEscape(actionUrl), HtmlUtils.htmlEscape(actionLabel));
        String paragraphs = HtmlUtils.htmlEscape(body).replace("\n", "<br>");
        return """
                <!doctype html><html><body style="margin:0;background:#f4f6f8;font-family:Arial,Helvetica,sans-serif">
                <table width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center" style="padding:24px">
                <table width="560" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px">
                <tr><td style="background:#1f4e79;color:#ffffff;padding:16px 24px;font-size:14px;font-weight:600;
                border-radius:8px 8px 0 0">Graduate Research Progress System</td></tr>
                <tr><td style="padding:24px;color:#1b1f23;font-size:15px;line-height:1.5">
                <h1 style="font-size:18px;margin:0 0 12px">%s</h1><p style="margin:0">%s</p>%s</td></tr>
                <tr><td style="padding:16px 24px;color:#5b6570;font-size:12px;border-top:1px solid #e3e7eb">
                You receive this because of your role in the graduate school. Manage email frequency in your profile.
                </td></tr></table></td></tr></table></body></html>"""
                .formatted(HtmlUtils.htmlEscape(heading), paragraphs, button);
    }

    public static String text(String heading, String body, String actionUrl) {
        return heading + "\n\n" + body + (actionUrl == null ? "" : "\n\nOpen: " + actionUrl)
                + "\n\n-- Graduate Research Progress System";
    }
}
