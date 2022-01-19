package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.sk7software.bincollection.storage.CustomerAddressDAO;
import com.sk7software.bincollection.storage.CustomerAddressDynamoDBClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;

public class PingRequestHandler implements RequestHandler {
    private static final Logger log = LoggerFactory.getLogger(PingRequestHandler.class);

    @Override
    public boolean canHandle(HandlerInput input) {
        return input.matches(intentName("PingRequest"));
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        log.debug("PingRequestHandler");
        AmazonDynamoDBClient amazonDynamoDBClient = new AmazonDynamoDBClient();
        amazonDynamoDBClient.setRegion(Region.getRegion(Regions.EU_WEST_1));
        CustomerAddressDynamoDBClient dynamoDbClient = new CustomerAddressDynamoDBClient(amazonDynamoDBClient);
        CustomerAddressDAO customerAddressDao = new CustomerAddressDAO(dynamoDbClient);
        customerAddressDao.ping();
        return Optional.empty();
    }
}
