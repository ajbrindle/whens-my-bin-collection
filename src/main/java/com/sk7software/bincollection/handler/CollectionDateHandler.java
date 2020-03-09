package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.model.Slot;
import com.amazon.ask.request.Predicates;
import com.amazon.ask.response.ResponseBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk7software.bincollection.exception.DeviceAddressClientException;
import com.sk7software.bincollection.exception.UnauthorizedException;
import com.sk7software.bincollection.model.*;
import com.sk7software.bincollection.storage.CustomerAddressDAO;
import com.sk7software.bincollection.storage.CustomerAddressDynamoDBClient;
import com.sk7software.bincollection.util.AlexaDeviceAddressClient;
import com.sk7software.bincollection.util.DateUtil;
import com.sk7software.bincollection.util.SpeechletUtils;
import org.apache.commons.io.IOUtils;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.amazon.ask.request.Predicates.intentName;

public class CollectionDateHandler implements RequestHandler {
    private static final Logger log = LoggerFactory.getLogger(com.sk7software.bincollection.handler.CollectionDateHandler.class);
    private static final String COLLECTION_URL = "http://www.sk7software.co.uk/bins?id=";
    private static final String ADDRESS_MATCH_URL = "http://www.sk7software.co.uk/bins/inputPostcode.php?";
    private static final String PROGRESSIVE_API_SUFFIX = "/v1/directives";

    private static final String KEY_MODE = "mode";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_BINS = "bins";

    private AmazonDynamoDBClient amazonDynamoDBClient;
    private CustomerAddressDAO customerAddressDao;

    private CustomerAddress customerAddress;
    private List<Bin> bins;
    private String uprn;
    private Mode interactiveMode;

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(intentName("BinCollectionIntent")) ||
                input.matches(intentName("EchoAddressIntent")) ||
                input.matches(intentName("BinColourIntent")) ||
                input.matches(intentName("ThankYouIntent")) ||
                input.matches(intentName("ClearAddressIntent")) ||
                input.matches(intentName("AMAZON.YesIntent")) ||
                input.matches(intentName("AMAZON.NoIntent")) ||
                input.matches(intentName("AMAZON.HelpIntent")) ||
                input.matches(intentName("AMAZON.StopIntent"));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        // Retrieve session values
        log.debug("Session attributes: " + input.getAttributesManager().getSessionAttributes());

        if (input.getAttributesManager().getSessionAttributes().containsKey(KEY_MODE)) {
            interactiveMode = Mode.valueOf((String)input.getAttributesManager().getSessionAttributes().get(KEY_MODE));
        } else {
            interactiveMode = Mode.NONE;
        }

        customerAddress = getAddressFromSession(input);

        openDatabase();
        log.debug("Database opened");

        if (input.matches(intentName("BinCollectionIntent")) ||
            input.matches(Predicates.requestType(LaunchRequest.class))) {
            return checkAddress(input);
        } else if (input.matches(intentName("EchoAddressIntent"))) {
            return getEchoAddressResponse(input);
        } else if (input.matches(intentName("ClearAddressIntent"))) {
            return clearAddressResponse(input);
        } else if (input.matches(intentName("BinColourIntent"))) {
            return getBinColourResponse(input);
        } else if (input.matches(intentName("AMAZON.YesIntent"))) {
            if (interactiveMode == Mode.ADDRESS) {
                interactiveMode = Mode.NONE;
                input.getAttributesManager().getSessionAttributes().put(KEY_MODE, interactiveMode.name());
                return getAddressMatchResponse(input);
            } else if (interactiveMode == Mode.ADDRESS_MATCH) {
                interactiveMode = Mode.NONE;
                input.getAttributesManager().getSessionAttributes().put(KEY_MODE, interactiveMode.name());
                return getAddressStoreResponse(input);
            } else if (interactiveMode == Mode.BINS) {
                interactiveMode = Mode.NONE;
                input.getAttributesManager().getSessionAttributes().put(KEY_MODE, interactiveMode.name());
                return getBinCollectionResponse(input);
            } else {
                return null; // TODO: return error
            }
        } else if (input.matches(intentName("ThankYouIntent")) ||
                   input.matches(intentName("AMAZON.NoIntent")) ||
                   input.matches(intentName("AMAZON.StopIntent"))) {
            return getStopResponse();
        }
//                input.matches(intentName("BinColourIntent")) ||
//                input.matches(intentName("AMAZON.HelpIntent")) ||
//        case "BinColourIntent":
//        return getBinColourResponse(intent);
//        case "AMAZON.HelpIntent":
//        return getHelpResponse();

        return Optional.empty();
    }

    public Optional<Response> checkAddress(final HandlerInput input) {

        try {
            log.debug("Checking Addres....");

            // See if address has been stored
            if (customerAddressDao.getAddress(input) == null) {
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
                    interactiveMode = Mode.ADDRESS;
                    input.getAttributesManager().getSessionAttributes().put(KEY_MODE, interactiveMode.name());
                    return addressLine1OptionalResponse();
                }
            } else {
                // There is a saved address so use it
                log.debug("Address check complete - looking up bin information");
                return getBinCollectionResponse(input);
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

    public Optional<Response> getBinCollectionResponse(HandlerInput input) {
        StringBuilder speechText = new StringBuilder();
        List<Bin> collectedBins = new ArrayList<>();
        DateTime collectionDate = new DateTime();

        try {
            CustomerAddress ma = customerAddressDao.getAddress(input);
            log.debug(ma.toString());

            // Kick-off call to get bin information asynchronously
            CompletableFuture<List<Bin>> completableFuture =
                    new CompletableFuture<>();

            Executors.newCachedThreadPool().submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        String url = COLLECTION_URL + ma.getUprn();
                        String binsStr = getJsonResponse(url);
                        log.debug("Response: " + binsStr);
                        completableFuture.complete(Bin.createFromJSON(new JSONObject(binsStr)));
                    } catch (JSONException | IOException e) {
                        completableFuture.complete(null);
                        log.error(e.getMessage());
                    }
                }
            });

            // Meanwhile, play progress message
            sendProgressMessage(input);

            // Get result of async call
            bins = completableFuture.get(8000, TimeUnit.MILLISECONDS);

            if (bins == null) {
                speechText.append("Sorry, there was a problem finding your bin collection dates");
            } else {
                ObjectMapper mapper = new ObjectMapper();
                input.getAttributesManager().getSessionAttributes().put(KEY_BINS, mapper.convertValue(bins, new TypeReference<List<Bin>>() {
                }));

                collectionDate = getNextCollectionDate(bins);

                if (collectionDate != null && !collectionDate.isBeforeNow()) {
                    collectedBins = getBinsCollectedOnDate(bins, collectionDate);

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
                input.getAttributesManager().getSessionAttributes().put(KEY_MODE, interactiveMode.name());
                input.getAttributesManager().getSessionAttributes().put("address", customerAddress);
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

    private Optional<Response> getAddressStoreResponse(HandlerInput input) {
        customerAddressDao.saveAddress(input, customerAddress);

        String speechText = "I have stored that address for you. " +
                "Would you like to hear the bin collection dates?";
        interactiveMode = Mode.BINS;
        input.getAttributesManager().getSessionAttributes().put(KEY_MODE, interactiveMode.name());

        return new ResponseBuilder()
                .withSpeech(speechText)
                .withReprompt("Would you like to hear your bin collection dates? Please say 'yes' or 'no")
                .build();
    }

    private Optional<Response> clearAddressResponse(HandlerInput input) {
        log.debug("Clear address intent");
        String speechText = "I have cleared your address details. Next time you request your bin collection dates " +
                "I will try to look up the address on your Echo device. Please make sure this address is correct. " +
                "Goodbye.";

        customerAddressDao.deleteAddress(input);

        return new ResponseBuilder()
                .withSpeech(speechText)
                .withShouldEndSession(true)
                .build();
    }

    private Optional<Response> getBinColourResponse(HandlerInput input) {
        StringBuilder speechText = new StringBuilder();
        List<Bin> thisBin = new ArrayList<>();
        DateTime collectionDate = null;

        bins = getBinsFromSession(input);

        if (bins != null && bins.size() > 0) {
            IntentRequest ireq = (IntentRequest)input.getRequestEnvelope().getRequest();
            Slot slot = ireq.getIntent().getSlots().get("colour");

            if (slot != null && slot.getValue() != null) {
                String binColour = slot.getValue();

                for (Bin b : bins) {
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

    private void openDatabase() {
        if (amazonDynamoDBClient == null) {
            amazonDynamoDBClient = new AmazonDynamoDBClient();
            amazonDynamoDBClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
        }

        if (customerAddressDao == null) {
            CustomerAddressDynamoDBClient dynamoDbClient = new CustomerAddressDynamoDBClient(amazonDynamoDBClient);
            customerAddressDao = new CustomerAddressDAO(dynamoDbClient);
        }
    }

    private Optional<Response> getStopResponse() {
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

        return new ResponseBuilder()
                .withSpeech(stopText)
                .withShouldEndSession(true)
                .build();
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
            con.setConnectTimeout(10000);
            con.setReadTimeout(20000);

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

    private Optional<Response> getEchoAddressResponse(HandlerInput input) {
        StringBuilder speechText = new StringBuilder("<speak>");
        EchoAddress echoAddress = null;
        CustomerAddress customerAddress;
        boolean error = false;

        try {
            echoAddress = fetchEchoAddress(input);
            customerAddress = customerAddressDao.getAddress(input);

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
//        SystemState systemState = input.getRequestEnvelope().getContext().getSystem(SystemInterface.class, SystemState.class);
        String deviceId = input.getRequestEnvelope().getContext().getSystem().getDevice().getDeviceId(); //systemState.getDevice().getDeviceId();
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

    private String getUnauthorizedExceptionResponse() {
        return "You have refused to allow access to your address information in the Alexa app. " +
                "The bin collection skill cannot function without address information. " +
                "To permit access to address information, enable this skill again, " +
                "and consent to provide address information in the Alexa app.";
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

    public static CustomerAddress getAddressFromSession(HandlerInput input) {
        try {
            if (input.getAttributesManager().getSessionAttributes().containsKey("address")) {
                JSONObject j = new JSONObject(input.getAttributesManager().getSessionAttributes());
                CustomerAddress a = CustomerAddress.createFromJSON(j.getJSONObject("address"));
                log.debug("Restored address: " + a.toString());
                return a;
            }
        } catch (JSONException je) {
            log.error("Unable to deserialise address: " + je.getMessage());
        } catch (IOException ie) {
            log.error("Unable to deserialise address: " + ie.getMessage());
        }
        return null;
    }

    public static List<Bin> getBinsFromSession(HandlerInput input) {
        try {
            if (input.getAttributesManager().getSessionAttributes().containsKey(KEY_BINS)) {
                JSONObject j = new JSONObject(input.getAttributesManager().getSessionAttributes());
                List<Bin> bs = Bin.createFromJSON(j);
                log.debug("Restored: " + bs);
                return bs;
            }
        } catch (JSONException je) {
            log.error("Unable to deserialise bin list: " + je.getMessage());
        } catch (IOException ie) {
            log.error("Unable to deserialise bin list: " + ie.getMessage());
        }
        return null;
    }

    private static void sendProgressMessage(HandlerInput input) {
        String authToken = input.getRequestEnvelope().getContext().getSystem().getApiAccessToken();
        String requestId = input.getRequestEnvelope().getRequest().getRequestId();
        String directiveURL = input.getRequestEnvelope().getContext().getSystem().getApiEndpoint() + PROGRESSIVE_API_SUFFIX;
        String speech = "I'm just looking up the bin collection dates for your address.";
        ProgressiveAPIRequest request = new ProgressiveAPIRequest(requestId, speech);

        OutputStreamWriter outputStream = null;

        try {
            URL url = new URL(directiveURL);
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            log.debug("Calling " + url.toString());

            // set up url connection to get retrieve information back
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + authToken);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoInput(true);
            con.setDoOutput(true);

            outputStream = new OutputStreamWriter(con.getOutputStream());
            JSONObject requestBody = new JSONObject(request);
            outputStream.write(requestBody.toString());
            outputStream.flush();

            if (con.getResponseCode() != 204) {
                log.error("ProgressiveAPI error: " + con.getResponseCode() + ": " + con.getResponseMessage());
            }
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            IOUtils.closeQuietly(outputStream);
        }
    }
}
