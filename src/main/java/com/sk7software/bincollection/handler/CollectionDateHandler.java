package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazon.ask.request.Predicates;
import com.amazon.ask.response.ResponseBuilder;
import com.amazonaws.util.json.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk7software.bincollection.model.Bin;
import com.sk7software.bincollection.model.CustomerAddress;
import com.sk7software.bincollection.model.Mode;
import com.sk7software.bincollection.util.DateUtil;
import com.sk7software.bincollection.util.SpeechletUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;
import static com.sk7software.bincollection.handler.HandlerHelper.KEY_BINS;

public class CollectionDateHandler extends BinCollectionHandler {
    private static final Logger log = LoggerFactory.getLogger(com.sk7software.bincollection.handler.CollectionDateHandler.class);

    public CollectionDateHandler(HandlerInput input) {
        super(input);
    }

    @Override
    public Optional<Response> handle() {
        // Retrieve session values
        log.debug("Session attributes: " + input.getAttributesManager().getSessionAttributes());

        if (input.matches(intentName("BinColourIntent"))) {
            return getBinColourResponse();
        } else if (input.matches(intentName("AMAZON.YesIntent")) &&
                (handlerHelper.getInteractiveMode() == Mode.BINS)) {
                handlerHelper.updateInteractiveMode(Mode.NONE, input);
            return getBinCollectionResponse();
        } else {
            return Optional.empty();
        }
    }

    public Optional<Response> getBinCollectionResponse() {
        StringBuilder speechText = new StringBuilder();
        List<Bin> collectedBins = new ArrayList<>();
        DateTime collectionDate = new DateTime();

        try {
            CustomerAddress ma = handlerHelper.getCustomerAddressDao().getAddress(input);
            log.debug(ma.toString());

            String url = HandlerHelper.COLLECTION_URL + ma.getUprn();
            String binsStr = HandlerHelper.getJsonResponse(url);
            log.debug("Response: " + binsStr);

            handlerHelper.setBins(Bin.createFromJSON(new JSONObject(binsStr)));

            ObjectMapper mapper = new ObjectMapper();
            input.getAttributesManager().getSessionAttributes()
                    .put(KEY_BINS, mapper.convertValue(handlerHelper.getBins(), new TypeReference<List<Bin>>(){}));

            collectionDate = getNextCollectionDate(handlerHelper.getBins());

            if (collectionDate != null) {
                collectedBins = getBinsCollectedOnDate(handlerHelper.getBins(), collectionDate);

                if (collectedBins.size() > 0) {
                    speechText.append("Your next bin collection is ");
                    speechText.append(DateUtil.getDayDescription(collectionDate));
                    speechText.append(". ");
                    speechText.append(Bin.getSpokenBinList(collectedBins));
                    speechText.append(" will be collected.");
                } else {
                    speechText.append("Sorry, I couldn't find any bins that are due for collection");
                }
            } else {
                speechText.append("Sorry, I couldn't work out your next collection date");
            }
        } catch (Exception e) {
            speechText.append("Sorry, there was a problem finding your bin collection dates");
            log.error(e.getMessage());
        }

        ResponseBuilder responseBuilder = SpeechletUtils.buildStandardAskResponse(speechText.toString(), true);

        if (collectedBins.size() > 0) {
            SpeechletUtils.addBinDisplay(input, collectedBins, collectionDate, responseBuilder);
        }
        return responseBuilder.build();
    }


    private Optional<Response> getBinColourResponse() {
        StringBuilder speechText = new StringBuilder();
        List<Bin> thisBin = new ArrayList<>();
        DateTime collectionDate = null;

        handlerHelper.setBinsFromSession(input);

        if (handlerHelper.getBins().size() > 0) {
            IntentRequest ireq = (IntentRequest)input.getRequestEnvelope().getRequest();
            Slot slot = ireq.getIntent().getSlots().get("colour");

            if (slot != null && slot.getValue() != null) {
                String binColour = slot.getValue();

                for (Bin b : handlerHelper.getBins()) {
                    if (binColour.equalsIgnoreCase(b.getColour())) {
                        speechText.append("Your ");
                        speechText.append(binColour);
                        speechText.append(" bin will next be collected ");
                        speechText.append(DateUtil.getDayDescription(b.getDate()));
                        thisBin.add(b);
                        collectionDate = b.getDate();
                    }
                }
            } else {
                speechText.append("Sorry I did not recognise the bin you requested.");
            }
        } else {
            speechText.append("Sorry I don't have any information about your bins.");
        }

        ResponseBuilder responseBuilder = SpeechletUtils.buildStandardAskResponse(speechText.toString(), false);

        if (thisBin.size() > 0) {
            SpeechletUtils.addBinDisplay(input, thisBin, collectionDate, responseBuilder);
        }

        return responseBuilder.build();
    }


    private DateTime getNextCollectionDate(List<Bin> bins) {
        DateTime min = new DateTime(9999, 12, 31, 0, 0);

        for (Bin b : bins) {
            if (b.getDate().isBefore(min)) {
                min = b.getDate();
            }
        }

        return min;
    }

    private List<Bin> getBinsCollectedOnDate(List<Bin> bins, DateTime date) {
        List<Bin> collectedBins = new ArrayList<>();

        for (Bin b : bins) {
            if (b.isCollectedOnDate(date)) {
                collectedBins.add(b);
            }
        }

        return collectedBins;
    }
}
