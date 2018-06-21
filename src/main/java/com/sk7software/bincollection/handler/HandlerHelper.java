package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.sk7software.bincollection.model.Bin;
import com.sk7software.bincollection.model.CustomerAddress;
import com.sk7software.bincollection.model.Mode;
import com.sk7software.bincollection.storage.CustomerAddressDAO;
import com.sk7software.bincollection.storage.CustomerAddressDynamoDBClient;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class HandlerHelper {
    private static final Logger log = LoggerFactory.getLogger(com.sk7software.bincollection.handler.HandlerHelper.class);

    public static final String COLLECTION_URL = "http://www.sk7software.co.uk/bins?id=";
    public static final String ADDRESS_MATCH_URL = "http://www.sk7software.co.uk/bins/inputPostcode.php?";

    public static final String KEY_MODE = "mode";
    public static final String KEY_ADDRESS = "address";
    public static final String KEY_BINS = "bins";

    private static HandlerHelper handlerHelper = null;

    private AmazonDynamoDBClient amazonDynamoDBClient;
    private CustomerAddressDAO customerAddressDao;

    private CustomerAddress customerAddress;
    private List<Bin> bins;
    private String uprn;
    private Mode interactiveMode;

    private HandlerHelper() {
        bins = new ArrayList<>();
        interactiveMode = Mode.NONE;
    }

    public static HandlerHelper getInstance(HandlerInput input) {
        if (handlerHelper == null) {
            handlerHelper = new HandlerHelper();
            handlerHelper.initialise(input);
        }

        return handlerHelper;
    }

    public AmazonDynamoDBClient getAmazonDynamoDBClient() {
        return amazonDynamoDBClient;
    }

    public void setAmazonDynamoDBClient(AmazonDynamoDBClient amazonDynamoDBClient) {
        this.amazonDynamoDBClient = amazonDynamoDBClient;
    }

    public CustomerAddressDAO getCustomerAddressDao() {
        return customerAddressDao;
    }

    public void setCustomerAddressDao(CustomerAddressDAO customerAddressDao) {
        this.customerAddressDao = customerAddressDao;
    }

    public CustomerAddress getCustomerAddress() {
        return customerAddress;
    }

    public void setCustomerAddress(CustomerAddress customerAddress) {
        this.customerAddress = customerAddress;
    }

    public List<Bin> getBins() {
        return bins;
    }

    public void setBins(List<Bin> bins) {
        this.bins = bins;
    }

    public String getUprn() {
        return uprn;
    }

    public void setUprn(String uprn) {
        this.uprn = uprn;
    }

    public Mode getInteractiveMode() {
        return interactiveMode;
    }

    public void setInteractiveMode(Mode interactiveMode) {
        this.interactiveMode = interactiveMode;
    }

    public void initialise(HandlerInput input) {
        if (input.getAttributesManager().getSessionAttributes().containsKey(KEY_MODE)) {
            setInteractiveMode(Mode.valueOf((String)input.getAttributesManager().getSessionAttributes().get(KEY_MODE)));
        } else {
            setInteractiveMode(Mode.NONE);
        }

        setAddressFromSession(input);
        openDatabase();
    }

    public void updateInteractiveMode(Mode mode, HandlerInput input) {
        setInteractiveMode(mode);
        input.getAttributesManager().getSessionAttributes().put(KEY_MODE, mode.name());
    }

    public void updateCustomerAddress(HandlerInput input) {
        input.getAttributesManager().getSessionAttributes().put(KEY_ADDRESS, customerAddress);
    }

    public void setAddressFromSession(HandlerInput input) {
        try {
            if (input.getAttributesManager().getSessionAttributes().containsKey(KEY_ADDRESS)) {
                JSONObject j = new JSONObject(input.getAttributesManager().getSessionAttributes());
                CustomerAddress a = CustomerAddress.createFromJSON(j.getJSONObject(KEY_ADDRESS));
                log.debug("Restored address: " + a.toString());
                customerAddress = a;
            }
        } catch (JSONException je) {
            log.error("Unable to deserialise address: " + je.getMessage());
        } catch (IOException ie) {
            log.error("Unable to deserialise address: " + ie.getMessage());
        }
    }

    public void setBinsFromSession(HandlerInput input) {
        try {
            if (input.getAttributesManager().getSessionAttributes().containsKey(KEY_BINS)) {
                JSONObject j = new JSONObject(input.getAttributesManager().getSessionAttributes());
                List<Bin> bs = Bin.createFromJSON(j);
                log.debug("Restored: " + bs);
                bins = bs;
            }
        } catch (JSONException je) {
            log.error("Unable to deserialise bin list: " + je.getMessage());
        } catch (IOException ie) {
            log.error("Unable to deserialise bin list: " + ie.getMessage());
        }
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

    public void saveAddress(HandlerInput input) {
        customerAddressDao.saveAddress(input, customerAddress);
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

}
