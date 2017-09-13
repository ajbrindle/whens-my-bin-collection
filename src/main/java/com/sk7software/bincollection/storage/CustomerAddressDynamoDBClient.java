package com.sk7software.bincollection.storage;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;

public class CustomerAddressDynamoDBClient {
    private final AmazonDynamoDBClient dynamoDBClient;

    public CustomerAddressDynamoDBClient(final AmazonDynamoDBClient dynamoDBClient) {
        this.dynamoDBClient = dynamoDBClient;
    }

    /**
     * Loads an item from DynamoDB by primary Hash Key. Callers of this method should pass in an
     * object which represents an item in the DynamoDB table item with the primary key populated.
     *
     * @param tableItem
     * @return
     */
    public CustomerAddressData loadItem(final CustomerAddressData tableItem) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        CustomerAddressData item = mapper.load(tableItem);
        return item;
    }

    /**
     * Stores an item to DynamoDB.
     *
     * @param tableItem
     */
    public void saveItem(final CustomerAddressData tableItem) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        mapper.save(tableItem);
    }

    public void deleteItem(final CustomerAddressData tableItem) {
        DynamoDBMapper mapper = createDynamoDBMapper();
        mapper.delete(tableItem);
    }

    /**
     * Creates a {@link DynamoDBMapper} using the default configurations.
     *
     * @return
     */
    private DynamoDBMapper createDynamoDBMapper() {
        return new DynamoDBMapper(dynamoDBClient);
    }

}
