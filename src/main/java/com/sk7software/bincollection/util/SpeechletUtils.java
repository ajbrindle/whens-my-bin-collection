package com.sk7software.bincollection.util;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.interfaces.display.*;
import com.amazon.ask.response.ResponseBuilder;
import com.amazonaws.util.StringUtils;
import com.sk7software.bincollection.model.Bin;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SpeechletUtils {
    private static final Logger log = LoggerFactory.getLogger(SpeechletUtils.class);
    public static final String REPROMPT_TEXT = "Anything Else?";

    public static ResponseBuilder buildStandardAskResponse(final String response, final boolean doCard) {
        // Create the plain text output.
        log.debug("Building response");
        if (doCard) {
            // Create the Simple card content.
            return new ResponseBuilder().withSpeech(StringEscapeUtils.escapeHtml4(response))
                    .withReprompt(REPROMPT_TEXT)
                    .withSimpleCard("When's my bin collection", response);
        } else {
            return new ResponseBuilder().withSpeech(StringEscapeUtils.escapeHtml4(response))
                    .withReprompt(REPROMPT_TEXT);
        }
    }

    public static void addBinDisplay(HandlerInput handlerInput,
                                     List<Bin> bins,
                                     DateTime collectionDate,
                                     ResponseBuilder responseBuilder) {
        if (bins.size() > 0) {
            if (new DeviceCapability(handlerInput).hasDisplay()) {
                log.debug("Device has display");
                StringBuilder artworkUrl = new StringBuilder("http://www.sk7software.co.uk/bins/makeImage.php?bins=");

                for (Bin bin : bins) {
                    artworkUrl.append(bin.getColour().toLowerCase());
                    artworkUrl.append(",");
                }

                // Strip off trailing comma
                artworkUrl.deleteCharAt(artworkUrl.length()-1);
                log.debug("URL: " + artworkUrl.toString());

                try {
                    String imageUrl = URLEncoder.encode(artworkUrl.toString(), "UTF-8");
                    String collectionDateStr = "Next collection: " + DateUtil.getDayDescription(collectionDate);
                    responseBuilder.addRenderTemplateDirective(createDisplayTemplate(artworkUrl.toString(), collectionDateStr));
                } catch (UnsupportedEncodingException e) {
                    log.debug("Error building URL: " + e.getMessage());
                }
            }
        }
    }

    private static Template createDisplayTemplate(String imageUrl, String title) {
        List<ImageInstance> artwork = new ArrayList<>();
        artwork.add(ImageInstance.builder()
                .withUrl(imageUrl)
                .build());
//        List<ImageInstance> transparent = new ArrayList<>();
//        artwork.add(ImageInstance.builder()
//                .withUrl("http://www.sk7software.co.uk/bins/images/transparent.png")
//                .build());
        return BodyTemplate7.builder()
                .withTitle(title)
                .withImage(Image.builder()
                        .withSources(artwork)
                        .build())
//                .withBackgroundImage(Image.builder()
//                        .withSources(artwork)
//                        .build())
                .build();
    }
}
