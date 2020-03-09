package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import com.amazon.ask.response.ResponseBuilder;

import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;
import static com.sk7software.bincollection.model.Mode.ADDRESS;
import static com.sk7software.bincollection.model.Mode.ADDRESS_MATCH;
import static com.sk7software.bincollection.model.Mode.THANKS;

public class StandardResponseHandler extends BinCollectionHandler {

    public StandardResponseHandler(HandlerInput input) {
        super(input);
    }

    @Override
    public Optional<Response> handle() {
        if (input.matches(intentName("ThankYouIntent")) ||
                input.matches(intentName("AMAZON.NoIntent")) ||
                input.matches(intentName("AMAZON.StopIntent"))) {
            return getStopResponse();
        } else if (input.matches(intentName("AMAZON.HelpIntent"))) {
            return getHelpResponse();
        } else {
            return Optional.empty();
        }
    }

    private Optional<Response> getStopResponse() {
        String stopText;

        switch (handlerHelper.getInteractiveMode()) {
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

    private Optional<Response> getHelpResponse() {
        StringBuilder helpText = new StringBuilder();
        helpText.append("You can ask when's my bin collection to get the date of ");
        helpText.append("your next collection and the bins that will be collected. ");
        helpText.append("You can ask when a particular colour bin will next be collected by saying, ");
        helpText.append("for example, when will my blue bin be collected. ");

        return new ResponseBuilder()
                .withSpeech(helpText.toString())
                .withReprompt("")
                .build();
    }
}
