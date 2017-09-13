/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sk7software.bincollection;

import com.amazon.speech.json.SpeechletRequestEnvelope;
import com.amazon.speech.slu.Intent;
import com.amazon.speech.slu.Slot;
import com.amazon.speech.speechlet.*;
import com.amazon.speech.speechlet.interfaces.system.SystemInterface;
import com.amazon.speech.speechlet.interfaces.system.SystemState;
import com.amazon.speech.ui.PlainTextOutputSpeech;
import com.amazon.speech.ui.Reprompt;

import com.amazon.speech.ui.SimpleCard;
import com.amazon.speech.ui.SsmlOutputSpeech;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.sk7software.bincollection.exception.DeviceAddressClientException;
import com.sk7software.bincollection.model.EchoAddress;
import com.sk7software.bincollection.model.Bin;
import com.sk7software.bincollection.model.CustomerAddress;
import com.sk7software.bincollection.model.Mode;
import com.sk7software.bincollection.storage.CustomerAddressDAO;
import com.sk7software.bincollection.storage.CustomerAddressDynamoDBClient;
import com.sk7software.bincollection.util.AlexaDeviceAddressClient;
import com.sk7software.bincollection.util.DateUtil;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Andrew
 */
public class WhensMyBinCollectionSpeechlet implements SpeechletV2 {
    private static final Logger log = LoggerFactory.getLogger(com.sk7software.bincollection.WhensMyBinCollectionSpeechlet.class);

    private static final String COLLECTION_URL = "http://www.sk7software.co.uk/bins?id=";
    private static final String ADDRESS_MATCH_URL = "http://www.sk7software.co.uk/bins/inputPostcode.php?";

    private AmazonDynamoDBClient amazonDynamoDBClient;
    private CustomerAddressDAO customerAddressDao;

    private String deviceId;
    private String consentToken;
    private CustomerAddress customerAddress;
    private List<Bin> bins;

    private Mode interactiveMode;

    @Override
    public void onSessionStarted(final SpeechletRequestEnvelope<SessionStartedRequest> speechletRequestEnvelope) {
        log.info("onSessionStarted requestId={}, sessionId={}",
                speechletRequestEnvelope.getRequest().getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());

        if (amazonDynamoDBClient == null) {
            amazonDynamoDBClient = new AmazonDynamoDBClient();
            amazonDynamoDBClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
        }

        CustomerAddressDynamoDBClient dynamoDbClient = new CustomerAddressDynamoDBClient(amazonDynamoDBClient);
        customerAddressDao = new CustomerAddressDAO(dynamoDbClient);
    }

    @Override
    public SpeechletResponse onLaunch(final SpeechletRequestEnvelope<LaunchRequest> speechletRequestEnvelope) {
        log.info("onLaunch requestId={}, sessionId={}",
                speechletRequestEnvelope.getRequest().getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());

        interactiveMode = Mode.NONE;

        try {
            // See if address has been stored
            if (customerAddressDao.getAddress(speechletRequestEnvelope.getSession()) == null) {
                // Address not saved for this user so try to look it up
                EchoAddress echoEchoAddress = fetchEchoAddress(speechletRequestEnvelope);
                if (echoEchoAddress.getAddressLine1() != null &&
                        echoEchoAddress.getPostalCode() != null) {
                    // Sufficient information to lookup address
                    return getAddressMatchResponse(speechletRequestEnvelope);
                } else if (echoEchoAddress.getPostalCode() == null) {
                    // Need postcode
                    return postcodeRequiredResponse();
                } else { // (echoAddress.getAddressLine1() == null)
                    // Recommend first line
                    interactiveMode = Mode.ADDRESS;
                    return addressLine1OptionalResponse();
                }
            } else {
                // There is a saved address so use it
                return getBinCollectionResponse(speechletRequestEnvelope.getSession());
            }
        } catch (DeviceAddressClientException de) {
            return null; // TODO: Fix this
        }
    }

    @Override
    public SpeechletResponse onIntent(final SpeechletRequestEnvelope<IntentRequest> speechletRequestEnvelope) {
        IntentRequest request = speechletRequestEnvelope.getRequest();
        Session session = speechletRequestEnvelope.getSession();
        log.info("onIntent requestId={}, sessionId={}, intentName={}", request.getRequestId(),
                session.getSessionId(), (request.getIntent() != null ? request.getIntent().getName() : "null"));

        Intent intent = request.getIntent();
        String intentName = (intent != null) ? intent.getName() : "Invalid";

        switch (intentName) {
            case "BinCollectionIntent":
                return getBinCollectionResponse(session);
            case "EchoAddressIntent":
                return getEchoAddressResponse(speechletRequestEnvelope);
            case "BinColourIntent":
                return getBinColourResponse(intent);
            case "ThankYouIntent":
                interactiveMode = Mode.THANKS;
                return getStopResponse();
            case "ClearAddressIntent":
                return clearAddressResponse(speechletRequestEnvelope);
            case "AMAZON.YesIntent":
                if (interactiveMode == Mode.ADDRESS) {
                    interactiveMode = Mode.NONE;
                    return getAddressMatchResponse(speechletRequestEnvelope);
                } else if (interactiveMode == Mode.ADDRESS_MATCH) {
                    interactiveMode = Mode.NONE;
                    return getAddressStoreResponse(speechletRequestEnvelope);
                } else if (interactiveMode == Mode.BINS) {
                    interactiveMode = Mode.NONE;
                    return getBinCollectionResponse(speechletRequestEnvelope.getSession());
                } else {
                    return null; // TODO: return error
                }
            case "AMAZON.NoIntent":
                return getStopResponse();
            case "AMAZON.HelpIntent":
                return getHelpResponse();
            case "AMAZON.StopIntent":
                return getStopResponse();
            default:
                return null; // TODO: return error
        }
    }

    @Override
    public void onSessionEnded(final SpeechletRequestEnvelope<SessionEndedRequest> speechletRequestEnvelope) {
        log.info("onSessionEnded requestId={}, sessionId={}",
                speechletRequestEnvelope.getRequest().getRequestId(),
                speechletRequestEnvelope.getSession().getSessionId());
    }


    private SpeechletResponse getBinCollectionResponse(Session session) {
        StringBuilder speechText = new StringBuilder();
        try {
            CustomerAddress ma = customerAddressDao.getAddress(session);

            String url = COLLECTION_URL + ma.getUprn();
            String binsStr = getJsonResponse(url);
            bins = Bin.createFromJSON(new JSONObject(binsStr));

            DateTime collectionDate = getNextCollectionDate(bins);
            if (collectionDate != null) {
                List<Bin> collectedBins = getBinsCollectedOnDate(bins, collectionDate);

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

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText.toString());

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("Anything else?");
        reprompt.setOutputSpeech(repromptSpeech);

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("When's My Bin Collection?");
        card.setContent(speechText.toString());
        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    private SpeechletResponse getBinColourResponse(Intent intent) {
        StringBuilder speechText = new StringBuilder();

        if (bins != null && bins.size() > 0) {
            Slot slot = intent.getSlot("colour");
            if (slot != null && slot.getValue() != null) {
                String binColour = slot.getValue();

                for (Bin b : bins) {
                    if (binColour.equalsIgnoreCase(b.getColour())) {
                        speechText.append("Your ");
                        speechText.append(binColour);
                        speechText.append(" bin will next be collected ");
                        speechText.append(DateUtil.getDayDescription(b.getDate()));
                    }
                }
            } else {
                speechText.append("Sorry I did not recognise the bin you requested.");
            }
        } else {
            speechText.append("Sorry I don't have any information about your bins.");
        }

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText.toString());

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("Anything else?");
        reprompt.setOutputSpeech(repromptSpeech);

        // Create the Simple card content.
        SimpleCard card = new SimpleCard();
        card.setTitle("When's My Bin Collection?");
        card.setContent(speechText.toString());
        return SpeechletResponse.newAskResponse(speech, reprompt, card);
    }

    private SpeechletResponse getEchoAddressResponse(SpeechletRequestEnvelope<IntentRequest> speechletRequestEnvelope) {
        StringBuilder speechText = new StringBuilder("<speak>");
        EchoAddress echoAddress = null;
        CustomerAddress customerAddress;

        try {
            echoAddress = fetchEchoAddress(speechletRequestEnvelope);
            customerAddress = customerAddressDao.getAddress(speechletRequestEnvelope.getSession());

            speechText.append("The address I have for you is: ");
            speechText.append("<say-as interpret-as=\"address\">");
            speechText.append(echoAddress.getSpokenAddress());
            speechText.append("</say-as>");
            speechText.append(". I have matched this to: ");
            speechText.append("<say-as interpret-as=\"address\">");
            speechText.append(customerAddress.getAddress());
            speechText.append("</say-as>");
        } catch (DeviceAddressClientException e) {
            speechText.append("Sorry, there was a problem finding your address");
            log.error(e.getMessage());
        }
        speechText.append("</speak>");

        SsmlOutputSpeech speech = new SsmlOutputSpeech();
        //PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setSsml(speechText.toString());

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("Anything else?");
        reprompt.setOutputSpeech(repromptSpeech);

        return SpeechletResponse.newAskResponse(speech, reprompt);
    }

    /*
     * Calls service to match Echo address with addresses for the supplied postcode
     */
    private SpeechletResponse getAddressMatchResponse(SpeechletRequestEnvelope<? extends SpeechletRequest> speechletRequestEnvelope) {
        StringBuilder speechText = new StringBuilder("<speak>");
        EchoAddress echoAddress = null;

        try {
            echoAddress = fetchEchoAddress(speechletRequestEnvelope);
            String url = ADDRESS_MATCH_URL +
                    (echoAddress.getAddressLine1() != null ? "address=" +
                        URLEncoder.encode(echoAddress.getAddressLine1(), "UTF-8") + "&" : "") +
                    (echoAddress.getPostalCode() != null ? "postcode=" +
                        URLEncoder.encode(echoAddress.getPostalCode(), "UTF-8") : "");
            log.info("Requesting: " + url);
            String matchedAddressStr = getJsonResponse(url);
            customerAddress = CustomerAddress.createFromJSON(new JSONObject(matchedAddressStr));

            if (customerAddress.getUprn() != null && !"".equals(customerAddress.getUprn())) {
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
                speechText.append(customerAddress.getAddress());
                speechText.append("</say-as>");
                speechText.append(". Is this address OK to use?");
                interactiveMode = Mode.ADDRESS_MATCH;
            } else {
                speechText.append("Sorry, I was unable to find an address that matches the one on your Echo. ");
                speechText.append("Please use the Amazon Alexa app to check your address details for this Echo device.");
            }
        } catch (DeviceAddressClientException e) {
            speechText.append("Sorry, there was a problem finding your address, please retry later");
            log.error(e.getMessage());
        } catch (UnsupportedEncodingException ue) {
            speechText.append("Sorry, there was a problem with the address I have for you. ");
            speechText.append("Please use the Amazon Alexa app to check your address details for this Echo device.");
            log.error(ue.getMessage());
        } catch (JSONException je) {
            speechText.append("Sorry, there was a problem matching your address.");
            log.error(je.getMessage());
        } catch (IOException ie) {
            speechText.append("Sorry, there was a problem matching your address.");
            log.error(ie.getMessage());
        }

        speechText.append("</speak>");
        SsmlOutputSpeech speech = new SsmlOutputSpeech();
        speech.setSsml(speechText.toString());

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("Is the address I have matched for you OK? Please say 'yes' or 'no");
        reprompt.setOutputSpeech(repromptSpeech);
        return SpeechletResponse.newAskResponse(speech, reprompt);
    }

    private SpeechletResponse getAddressStoreResponse(SpeechletRequestEnvelope<IntentRequest> speechletRequestEnvelope) {
        customerAddressDao.saveAddress(speechletRequestEnvelope.getSession(), customerAddress);

        String speechText = "I have stored that address for you. " +
                            "Would you like to hear the bin collection dates?";
        interactiveMode = Mode.BINS;

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("Would you like to hear your bin collection dates? Please say 'yes' or 'no");
        reprompt.setOutputSpeech(repromptSpeech);
        return SpeechletResponse.newAskResponse(speech, reprompt);

    }

    private SpeechletResponse postcodeRequiredResponse() {
        String speechText = "I need to know at least your postcode to find your bin collection dates. " +
                            "Please set your address for this Echo device in the Amazon Alexa app. " +
                            "When you have done this, ask me to find your bin collection dates again.";

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        SpeechletResponse response = new SpeechletResponse();
        response.setOutputSpeech(speech);
        response.setShouldEndSession(true);
        return response;
    }

    private SpeechletResponse addressLine1OptionalResponse() {
        String speechText = "I have found your post code, but I might need the first line of your address too. " +
                            "If you think this is needed, please enter more address details for thie Echo device " +
                            "in the Amazon Alexa app. Do you want to continue with the post code only.";

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("Say 'yes' to continue with only the post code, or 'no' to stop.");
        reprompt.setOutputSpeech(repromptSpeech);

        return SpeechletResponse.newAskResponse(speech, reprompt);
    }

    private SpeechletResponse clearAddressResponse(SpeechletRequestEnvelope<? extends SpeechletRequest> speechletRequestEnvelope) {
        String speechText = "I have cleared your address details. Next time you request your bin collection dates " +
                            "I will try to look up the address on your Echo device. Please make sure this address is correct. " +
                            "Goodbye.";

        customerAddressDao.deleteAddress(speechletRequestEnvelope.getSession());

        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(speechText);
        SpeechletResponse response = new SpeechletResponse();
        response.setShouldEndSession(true);
        response.setOutputSpeech(speech);
        return response;
    }

    private EchoAddress fetchEchoAddress(SpeechletRequestEnvelope<? extends SpeechletRequest> speechletRequestEnvelope)
        throws DeviceAddressClientException {
        SystemState systemState = speechletRequestEnvelope.getContext().getState(SystemInterface.class, SystemState.class);
        deviceId = systemState.getDevice().getDeviceId();
        consentToken = speechletRequestEnvelope.getSession().getUser().getPermissions().getConsentToken();

        String apiEndpoint = systemState.getApiEndpoint();

        AlexaDeviceAddressClient alexaDeviceAddressClient = new AlexaDeviceAddressClient(
                deviceId, consentToken, apiEndpoint);

        EchoAddress echoAddress = alexaDeviceAddressClient.getFullAddress();
        return echoAddress;
    }

    public static String getJsonResponse(String requestURL) {
        InputStreamReader inputStream = null;
        BufferedReader bufferedReader = null;
        String text;
        try {
            String line;
            URL url = new URL(requestURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // set up url connection to get retrieve information back
            con.setRequestMethod("GET");

            inputStream = new InputStreamReader(con.getInputStream(), Charset.forName("US-ASCII"));
            bufferedReader = new BufferedReader(inputStream);
            StringBuilder builder = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                builder.append(line);
            }
            text = builder.toString();
        } catch (IOException e) {
            // reset text variable to a blank string
            log.error(e.getMessage());
            text = "";
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(bufferedReader);
        }

        return text;
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

    /**
     * Creates a {@code SpeechletResponse} for the help intent.
     *
     * @return SpeechletResponse spoken and visual response for the given intent
     */
    private SpeechletResponse getHelpResponse() {
        StringBuilder helpText = new StringBuilder();
        helpText.append("You can ask when's my bin collection to get the date of ");
        helpText.append("your next collection and the bins that will be collected. ");
        helpText.append("You can ask when a particular colour bin will next be collected by saying, ");
        helpText.append("for example, when will my blue bin be collected. ");

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(helpText.toString());

        // Create reprompt
        Reprompt reprompt = new Reprompt();
        PlainTextOutputSpeech repromptSpeech = new PlainTextOutputSpeech();
        repromptSpeech.setText("");
        reprompt.setOutputSpeech(repromptSpeech);

        return SpeechletResponse.newAskResponse(speech, reprompt);
    }

    private SpeechletResponse getStopResponse() {
        String stopText;

        switch (interactiveMode) {
            case ADDRESS:
                stopText = "Please enter your full address in the Amazon Alexa app for this Echo device.";
                break;
            case ADDRESS_MATCH:
                stopText = "Sorry, I won't be able to find your bin collection information if I can't match your address. " +
                        "Please check that the address details for this Echo device are correct in the Amazon Alexa app. " +
                        "If not, please correct them and retry.";
                break;
            case THANKS:
                stopText = "You're welcome. Goodbye.";
                break;
            default:
                stopText = "Goodbye.";
        }

        // Create the plain text output.
        PlainTextOutputSpeech speech = new PlainTextOutputSpeech();
        speech.setText(stopText);

        SpeechletResponse stopResponse = new SpeechletResponse();
        stopResponse.setShouldEndSession(true);
        stopResponse.setOutputSpeech(speech);
        
        return stopResponse;
    }

}
