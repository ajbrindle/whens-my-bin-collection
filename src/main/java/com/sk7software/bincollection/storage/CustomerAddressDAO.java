package com.sk7software.bincollection.storage;

import com.amazon.speech.speechlet.Session;
import com.sk7software.bincollection.model.CustomerAddress;

public class CustomerAddressDAO {
    private final CustomerAddressDynamoDBClient dynamoDbClient;

    public CustomerAddressDAO(CustomerAddressDynamoDBClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    public CustomerAddress getAddress(Session session) {
        CustomerAddressData item = new CustomerAddressData();
        item.setCustomerId(session.getUser().getUserId());

        item = dynamoDbClient.loadItem(item);

        if (item == null) {
            return null;
        }

        return item.getAddressItem();
    }

    public void saveAddress(Session session, CustomerAddress ma) {
        CustomerAddressData item = new CustomerAddressData();
        item.setCustomerId(session.getUser().getUserId());
        item.setAddressItem(ma);

        dynamoDbClient.saveItem(item);
    }

    public void deleteAddress(Session session) {
        CustomerAddressData item = new CustomerAddressData();
        item.setCustomerId(session.getUser().getUserId());
        dynamoDbClient.deleteItem(item);
    }

}
