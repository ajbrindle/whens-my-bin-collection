package com.sk7software.bincollection.storage;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sk7software.bincollection.model.CustomerAddress;

@DynamoDBTable(tableName = "CustomerAddress")
public class CustomerAddressData {
    private String customerId;
    private CustomerAddress addressItem;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @DynamoDBHashKey(attributeName = "CustomerId")
    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    @DynamoDBAttribute(attributeName = "AddressData")
    @DynamoDBMarshalling(marshallerClass = MatchedAddressMarshaller.class)
    public CustomerAddress getAddressItem() {
        return addressItem;
    }

    public void setAddressItem(CustomerAddress addressItem) {
        this.addressItem = addressItem;
    }

    public static class MatchedAddressMarshaller implements
            DynamoDBMarshaller<CustomerAddress> {

        @Override
        public String marshall(CustomerAddress addressItem) {
            try {
                return OBJECT_MAPPER.writeValueAsString(addressItem);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Unable to marshall address data", e);
            }
        }

        @Override
        public CustomerAddress unmarshall(Class<CustomerAddress> clazz, String value) {
            try {
                return OBJECT_MAPPER.readValue(value, new TypeReference<CustomerAddress>() {
                });
            } catch (Exception e) {
                throw new IllegalStateException("Unable to unmarshall address value", e);
            }
        }
    }
}
