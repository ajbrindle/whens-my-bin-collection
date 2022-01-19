package com.sk7software.bincollection.util;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.interfaces.alexa.presentation.apl.RenderDocumentDirective;
import com.amazon.ask.model.interfaces.display.*;
import com.amazon.ask.response.ResponseBuilder;
import com.amazon.ask.request.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk7software.bincollection.model.Bin;
import org.apache.commons.lang3.StringEscapeUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

            if (RequestHelper.forHandlerInput(handlerInput).getSupportedInterfaces().getAlexaPresentationAPL() != null) {
                log.debug("Device has display");
                StringBuilder artworkUrl = new StringBuilder("https://www.sk7software.co.uk/bins/makeImage.php?bins=");

                for (Bin bin : bins) {
                    artworkUrl.append(bin.getColour().toLowerCase());
                    artworkUrl.append(",");
                }

                // Strip off trailing comma
                artworkUrl.deleteCharAt(artworkUrl.length()-1);
                log.debug("URL: " + artworkUrl.toString());

                try {
                    String collectionDateStr = "Next collection: " + DateUtil.getDayDescription(collectionDate);

                    String documentName = "binDisplay.json";
                    String token = "binDisplayToken";

                    ObjectMapper mapper = new ObjectMapper();
                    TypeReference<HashMap<String, Object>> documentMapType = new TypeReference<HashMap<String, Object>>() {
                    };

                    Map<String, Object> document = mapper.readValue(new File(documentName), documentMapType);
                    Map<String, Object> data = new HashMap<>();
                    Map<String, String> dataValues = new HashMap<>();

                    dataValues.put("text", collectionDateStr);
                    dataValues.put("imageSource", artworkUrl.toString());
                    data.put("binDisplayData", dataValues);

                    RenderDocumentDirective documentDirective = RenderDocumentDirective.builder()
                            .withToken(token)
                            .withDocument(document)
                            .withDatasources(data)
                            .build();
                    responseBuilder.addDirective(documentDirective);
                } catch (Exception e) {
                    log.debug("Error building URL: " + e.getMessage());
                }
            } else {
                log.debug("Device has no display");
            }
        }
    }
}
