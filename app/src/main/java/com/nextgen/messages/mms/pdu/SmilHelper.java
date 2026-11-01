package com.nextgen.messages.mms.pdu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.nio.charset.StandardCharsets;

/**
 * Creates standards-compliant SMIL presentation parts for MMS PDUs.
 */
public final class SmilHelper {

    private SmilHelper() {}

    @NonNull
    public static PduPart createSmilPart(@Nullable String textSrc, @Nullable String mediaSrc, @Nullable String mediaType) {
        StringBuilder smil = new StringBuilder();
        smil.append("<smil><head><layout>")
            .append("<root-layout width=\"320px\" height=\"480px\"/>")
            .append("<region id=\"Image\" width=\"100%\" height=\"70%\" fit=\"meet\" top=\"0\" left=\"0\"/>")
            .append("<region id=\"Text\" width=\"100%\" height=\"30%\" top=\"70%\" left=\"0\"/>")
            .append("</layout></head><body><par dur=\"8000ms\">");

        if (mediaSrc != null && mediaType != null) {
            if (mediaType.startsWith("image/")) {
                smil.append("<img src=\"").append(escapeXml(mediaSrc)).append("\" region=\"Image\" />");
            } else if (mediaType.startsWith("video/")) {
                smil.append("<video src=\"").append(escapeXml(mediaSrc)).append("\" region=\"Image\" />");
            } else if (mediaType.startsWith("audio/")) {
                smil.append("<audio src=\"").append(escapeXml(mediaSrc)).append("\" />");
            }
        }

        if (textSrc != null) {
            smil.append("<text src=\"").append(escapeXml(textSrc)).append("\" region=\"Text\" />");
        }

        smil.append("</par></body></smil>");

        byte[] smilBytes = smil.toString().getBytes(StandardCharsets.UTF_8);

        PduPart part = new PduPart();
        part.setContentType("application/smil".getBytes(StandardCharsets.US_ASCII));
        part.setContentId("<smil>".getBytes(StandardCharsets.US_ASCII));
        part.setContentLocation("smil.xml".getBytes(StandardCharsets.US_ASCII));
        part.setData(smilBytes);
        return part;
    }

    private static String escapeXml(String str) {
        return str.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
