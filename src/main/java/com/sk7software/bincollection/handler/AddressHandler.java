package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import com.amazon.ask.response.ResponseBuilder;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.sk7software.bincollection.exception.DeviceAddressClientException;
import com.sk7software.bincollection.exception.UnauthorizedException;
import com.sk7software.bincollection.model.CustomerAddress;
import com.sk7software.bincollection.model.EchoAddress;
import com.sk7software.bincollection.model.Mode;
import com.sk7software.bincollection.util.AlexaDeviceAddressClient;
import com.sk7software.bincollection.util.SpeechletUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;
import static com.sk7software.bincollection.handler.HandlerHelper.ADDRESS_MATCH_URL;
import static com.sk7software.bincollection.handler.HandlerHelper.KEY_ADDRESS;
import static com.sk7software.bincollection.handler.HandlerHelper.KEY_MODE;

public class AddressHandler extends BinCollectionHandler {
    private static final Logger log = LoggerFactory.getLogger(com.sk7software.bincollection.handler.AddressHandler.class);

    public AddressHandler(HandlerInput input) {
        super(input);
    }

    @Override
    public Optional<Response> handle() {
        log.debug("Session attributes: " + input.getAttributesManager().getSessionAttributes());

        if (input.matches(intentName("BinCollectionIntent")) ||
                input.matches(Predicates.requestType(LaunchRequest.class))) {
            return checkAddress(input);
        } else if (input.matches(intentName("EchoAddressIntent"))) {
            return getEchoAddressResponse(input);
        } else if (input.matches(intentName("ClearAddressIntent"))) {
            return clearAddressResponse(input);
        } else if (input.matches(intentName("AMAZON.YesIntent"))) {
            if (handlerHelper.getInteractiveMode() == Mode.ADDRESS) {
                handlerHelper.updateInteractiveMode(Mode.NONE, input);
                return getAddressMatchResponse(input);
            } else if (handlerHelper.getInteractiveMode() == Mode.ADDRESS_MATCH) {
                handlerHelper.updateInteractiveMode(Mode.NONE, input);
                return getAddressStoreResponse(input);
            } else {
                return Optional.empty();
            }
        }

        return Optional.empty();
    }

    private Optional<Response> checkAddress(final HandlerInput input) {
        try {
            // See if address has been stored
            if (handlerHelper.getCustomerAddressDao().getAddress(input) == null) {
                // Address not saved for this user so try to look it up
                EchoAddress echoEchoAddress = fetchEchoAddress(input);
                if (echoEchoAddress.getAddressLine1() != null &&
                        echoEchoAddress.getPostalCode() != null) {
                    // Sufficient information to lookup address
                    return getAddressMatchResponse(input);
                } else if (echoEchoAddress.getPostalCode() == null) {
                    // Need postcode
                    return postcodeRequiredResponse();
                } else {
                    // Recommend first line
                    handlerHelper.updateInteractiveMode(Mode.ADDRESS, input);
                    return addressLine1OptionalResponse();
                }
            } else {
                // There is a saved address so use it
                return new CollectionDateHandler(input)
                        .getBinCollectionResponse();
            }
        } catch (UnauthorizedException ue) {
            return new ResponseBuilder()
                    .withSpeech(getUnauthorizedExceptionResponse())
                    .withShouldEndSession(true)
                    .build();
        } catch (DeviceAddressClientException de) {
            return new ResponseBuilder()
                    .withSpeech("An error has occurred looking up your device address. " +
                            "Please use the Amazon Alexa app to fix the address for your Echo device.")
                    .withShouldEndSession(true)
                    .build();
        }
    }

    private Optional<Response> getAddressMatchResponse(HandlerInput input) {
        StringBuilder speechText = new StringBuilder("<speak>");
        EchoAddress echoAddress = null;
        boolean error = false;

        try {
            echoAddress = fetchEchoAddress(input);
            String url = ADDRESS_MATCH_URL +
                    (echoAddress.getAddressLine1() != null ? "address=" +
                            URLEncoder.encode(echoAddress.getAddressLine1(), "UTF-8") + "&" : "") +
                    (echoAddress.getPostalCode() != null ? "postcode=" +
                            URLEncoder.encode(echoAddress.getPostalCode(), "UTF-8") : "");
            log.info("Requesting: " + url);
            String matchedAddressStr = HandlerHelper.getJsonResponse(url);
            handlerHelper.setCustomerAddress(CustomerAddress.createFromJSON(new JSONObject(matchedAddressStr)));

            if (handlerHelper.getCustomerAddress().getUprn() != null &&
                    !"".equals(handlerHelper.getCustomerAddress().getUprn())) {
                if (echoAddress.getAddressLine1() != null) {
                    speechText.append("The address I have for you is: ");
                    speechText.append("<say-as interpret-as=\"address\">");
                    speechText.append(echoAddress.getAddressLine1());
                    speechText.append(", ");
                } else {
                    speechText.append("<say-as interpret-as=\"address\">");
                    speechText.append("The post code I have for you is: ");
                }
                speechText.append(echoAddress.getPostalCode());
                speechText.append("</say-as>");
                speechText.append(". I have matched this to: ");
                speechText.append("<say-as interpret-as=\"address\">");
                speechText.append(handlerHelper.getCustomerAddress().getAddress());
                speechText.append("</say-as>");
                speechText.append(". Is this address OK to use?");
                handlerHelper.updateInteractiveMode(Mode.ADDRESS_MATCH, input);
                handlerHelper.updateCustomerAddress(input);
            } else {
                speechText.append("Sorry, I was unable to find an address that matches the one on your Echo. ");
                speechText.append("Please use the Amazon Alexa app to check your address details for this Echo device.");
            }
        } catch (UnauthorizedException ue) {
            error = true;
            speechText.append(getUnauthorizedExceptionResponse());
        } catch (DeviceAddressClientException e) {
            error = true;
            speechText.append("Sorry, there was a problem finding your address, please retry later");
            log.error(e.getMessage());
        } catch (UnsupportedEncodingException ue) {
            error = true;
            speechText.append("Sorry, there was a problem with the address I have for you. ");
            speechText.append("Please use the Amazon Alexa app to check your address details for this Echo device.");
            log.error(ue.getMessage());
        } catch (JSONException je) {
            error = true;
            speechText.append("Sorry, there was a problem matching your address.");
            log.error(je.getMessage());
        } catch (IOException ie) {
            error = true;
            speechText.append("Sorry, there was a problem matching your address.");
            log.error(ie.getMessage());
        }

        speechText.append("</speak>");

        return new ResponseBuilder()
                .withSpeech(speechText.toString())
                .withReprompt("Is the address I have matched for you OK? Please say 'yes' or 'no")
                .withShouldEndSession(error)
                .build();
    }

    private Optional<Response> clearAddressResponse(HandlerInput input) {
        log.debug("Clear address intent");
        String speechText = "I have cleared your address details. Next time you request your bin collection dates " +
                "I will try to look up the address on your Echo device. Please make sure this address is correct. " +
                "Goodbye.";

        handlerHelper.getCustomerAddressDao().deleteAddress(input);

        return new ResponseBuilder()
                .withSpeech(speechText)
                .withShouldEndSession(true)
                .build();
    }

    private Optional<Response> getAddressStoreResponse(HandlerInput input) {
        handlerHelper.saveAddress(input);

        String speechText = "I have stored that address for you. " +
                "Would you like to hear the bin collection dates?";
        handlerHelper.updateInteractiveMode(Mode.BINS, input);

        return new ResponseBuilder()
                .withSpeech(speechText)
                .withReprompt("Would you like to hear your bin collection dates? Please say 'yes' or 'no")
                .build();
    }

    public Optional<Response> getEchoAddressResponse(HandlerInput input) {
        StringBuilder speechText = new StringBuilder("<speak>");
        EchoAddress echoAddress = null;
        CustomerAddress customerAddress;
        boolean error = false;

        try {
            echoAddress = fetchEchoAddress(input);
            customerAddress = handlerHelper.getCustomerAddressDao().getAddress(input);

            speechText.append("The address I have for you is: ");
            speechText.append("<say-as interpret-as=\"address\">");
            speechText.append(echoAddress.getSpokenAddress());
            speechText.append("</say-as>");
            speechText.append(". I have matched this to: ");
            speechText.append("<say-as interpret-as=\"address\">");
            speechText.append(customerAddress.getAddress());
            speechText.append("</say-as>");
        } catch (UnauthorizedException ue) {
            error = true;
            speechText.append(getUnauthorizedExceptionResponse());
        } catch (DeviceAddressClientException e) {
            error = true;
            speechText.append("Sorry, there was a problem finding your address");
            log.error(e.getMessage());
        }
        speechText.append("</speak>");

        ResponseBuilder responseBuilder = SpeechletUtils.buildStandardAskResponse(speechText.toString(), false);
        responseBuilder.withShouldEndSession(error);
        return responseBuilder.build();
    }

    private EchoAddress fetchEchoAddress(HandlerInput input)
            throws DeviceAddressClientException, UnauthorizedException {
        String deviceId = input.getRequestEnvelope().getContext().getSystem().getDevice().getDeviceId();
        String consentToken = input.getRequestEnvelope().getSession().getUser().getPermissions().getConsentToken();

        if (consentToken == null || "".equals(consentToken)) {
            throw new UnauthorizedException("Address consent not provided");
        }

        String apiEndpoint = input.getRequestEnvelope().getContext().getSystem().getApiEndpoint(); //systemState.getApiEndpoint();

        AlexaDeviceAddressClient alexaDeviceAddressClient = new AlexaDeviceAddressClient(
                deviceId, consentToken, apiEndpoint);

        EchoAddress echoAddress = alexaDeviceAddressClient.getFullAddress();
//        echoAddress.setAddressLine1("34 The Crescent");
//        echoAddress.setPostalCode("SK3 8SN");
        return echoAddress;
    }

    private Optional<Response> postcodeRequiredResponse() {
        String speechText = "I need to know at least your postcode to find your bin collection dates. " +
                "Please set your address for this Echo device in the Amazon Alexa app. " +
                "When you have done this, ask me to find your bin collection dates again.";

        return new ResponseBuilder()
                .withSpeech(speechText)
                .withShouldEndSession(true)
                .build();    }

    private Optional<Response> addressLine1OptionalResponse() {
        String speechText = "I have found your post code, but I might need the first line of your address too. " +
                "If you think this is needed, please enter more address details for thie Echo device " +
                "in the Amazon Alexa app. Do you want to continue with the post code only.";

        return new ResponseBuilder()
                .withSpeech(speechText)
                .withReprompt("Say 'yes' to continue with only the post code, or 'no' to stop.")
                .build();
    }

    private String getUnauthorizedExceptionResponse() {
        return "You have refused to allow access to your address information in the Alexa app. " +
                "The bin collection skill cannot function without address information. " +
                "To permit access to address information, enable this skill again, " +
                "and consent to provide address information in the Alexa app.";
    }
}
