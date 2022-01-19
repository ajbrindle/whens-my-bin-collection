package com.sk7software.bincollection.model;

/*
 * Copyright 2016-2017 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use
 * this file except in compliance with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * This is a wrapper class that mimics the JSON structure returned from the Alexa Device Address API.
 * Refer to the Alexa Device Address API documentation on https://developer.amazon.com/ for more info.
 * Note that depending on the API path that you hit, not all properties will be populated.
 */
public class EchoAddress {

    private String stateOrRegion;
    private String city;
    private String countryCode;
    private String postalCode;
    private String addressLine1;
    private String addressLine2;
    private String addressLine3;
    private String districtOrCounty;

    private EchoAddress() {
    }

    public String getStateOrRegion() {
        return stateOrRegion;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getAddressLine2() {
        return addressLine2;
    }

    public String getAddressLine3() {
        return addressLine3;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getDistrictOrCounty() {
        return districtOrCounty;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public void setAddressLine1(String addressLine1) {
        this.addressLine1 = addressLine1;
    }

    public String getSpokenAddress() {
        StringBuilder addressString = new StringBuilder();

        if (addressLine1 != null) {
            addressString.append(addressLine1);
            addressString.append(", ");
        }
        addressString.append(postalCode);

        return addressString.toString();
    }

    public String toString() {
        return getAddressLine1() + ", " +
                getAddressLine2() + ", " +
                getAddressLine3() + ", " +
                getDistrictOrCounty() + ", " +
                getCity() + ", " +
                getStateOrRegion() + ", " +
                getPostalCode() + ", " +
                getCountryCode();
    }
}