package com.sk7software.bincollection.storage;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.sk7software.bincollection.model.CustomerAddress;

public class CustomerAddressDAO {
    private final CustomerAddressDynamoDBClient dynamoDbClient;

    public CustomerAddressDAO(CustomerAddressDynamoDBClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public CustomerAddress getAddress(HandlerInput input) {
        CustomerAddressData item = new CustomerAddressData();
        item.setCustomerId(input.getRequestEnvelope().getSession().getUser().getUserId());

        item = dynamoDbClient.loadItem(item);

        if (item == null) {
            return null;
        }

        return item.getAddressItem();
    }

    public void saveAddress(HandlerInput input, CustomerAddress ma) {
        CustomerAddressData item = new CustomerAddressData();
        item.setCustomerId(input.getRequestEnvelope().getSession().getUser().getUserId());
        item.setAddressItem(ma);

        dynamoDbClient.saveItem(item);
    }

    public void deleteAddress(HandlerInput input) {
        CustomerAddressData item = new CustomerAddressData();
        item.setCustomerId(input.getRequestEnvelope().getSession().getUser().getUserId());
        dynamoDbClient.deleteItem(item);
    }

}
