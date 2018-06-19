package com.sk7software.bincollection.model;

import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;

public class CustomerAddress {
    private String uprn;
    private String address;

    public CustomerAddress() {}

    public static CustomerAddress createFromJSON(JSONObject response) throws IOException, JSONException {
        CustomerAddress customerAddress;
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        customerAddress = mapper.readValue(response.toString(), CustomerAddress.class);

        return customerAddress;
    }

    public String getUprn() {
        return uprn;
    }

    public void setUprn(String uprn) {
        this.uprn = uprn;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String toString() {
        return address + " (" + uprn + ")";
    }
}
