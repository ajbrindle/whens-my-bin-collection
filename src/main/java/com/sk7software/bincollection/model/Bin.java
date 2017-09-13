package com.sk7software.bincollection.model;

import com.amazonaws.util.json.JSONArray;
import com.amazonaws.util.json.JSONException;
import com.amazonaws.util.json.JSONObject;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.io.IOException;
import java.util.List;

public class Bin {
    private String colour;

    @JsonDeserialize(using = JSONDateAdapter.class)
    private DateTime date;

    public Bin() {}

    public Bin(String colour, DateTime date) {
        this.colour = colour;
        this.date = date;
    }

    public static List<Bin> createFromJSON(JSONObject response) throws IOException, JSONException {
        Bin bin;

        JSONArray responseArray = response.getJSONArray("bins");
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Bin> bins = mapper.readValue(responseArray.toString(), new TypeReference<List<Bin>>(){});


        return bins;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public DateTime getDate() {
        return date;
    }

    public void setDate(DateTime date) {
        this.date = date;
    }

    public boolean isCollectedOnDate(DateTime collectionDate) {
        if (getDate().withTimeAtStartOfDay().equals(collectionDate.withTimeAtStartOfDay())) {
            return true;
        }
        return false;
    }

    public static String getSpokenBinList(List<Bin> bins) {
        StringBuilder speechText = new StringBuilder();
        speechText.append("The ");

        int numBins = bins.size();

        for (int i=0; i<numBins; i++) {
            speechText.append(bins.get(i).getColour());
            if (i == numBins-2) {
                speechText.append(" and ");
            } else if (numBins > 1 && i < numBins-1) {
                speechText.append(", ");
            }
        }
        if (numBins > 1) {
            speechText.append(" bins");
        } else if (numBins == 1) {
            speechText.append(" bin");
        }

        return speechText.toString();
    }
}
