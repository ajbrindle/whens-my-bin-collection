package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.dispatcher.request.handler.RequestHandler;
import com.amazon.ask.model.LaunchRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.request.Predicates;
import com.amazon.ask.response.ResponseBuilder;
import com.sk7software.bincollection.model.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static com.amazon.ask.request.Predicates.intentName;

public class HandleEverythingHandler implements RequestHandler {
    private static final Logger log = LoggerFactory.getLogger(com.sk7software.bincollection.handler.HandleEverythingHandler.class);

    @Override
    public boolean canHandle(HandlerInput handlerInput) {
        return true;
    }

    @Override
    public Optional<Response> handle(HandlerInput input) {
        // Retrieve session values
        log.debug("Session attributes: " + input.getAttributesManager().getSessionAttributes());

        Optional<Response> response = new CollectionDateHandler(input).handle();

        if (response.isPresent()) {
            return response;
        } else {
            response = new AddressHandler(input).handle();
            if (response.isPresent()) {
                return response;
            } else {
                return new StandardResponseHandler(input).handle();
            }
        }

//        if (input.matches(intentName("BinCollectionIntent")) ||
//                input.matches(Predicates.requestType(LaunchRequest.class))) {
//            return new AddressHandler().handle(input);
//        } else if (input.matches(intentName("EchoAddressIntent"))) {
//            return new AddressHandler().handle(input);
//        } else if (input.matches(intentName("ClearAddressIntent"))) {
//            return new AddressHandler().handle(input);
//        } else if (input.matches(intentName("BinColourIntent"))) {
//            return new CollectionDateHandler().handle(input);
//        } else if (input.matches(intentName("AMAZON.YesIntent"))) {
//            if (handlerHelper.getInteractiveMode() == Mode.ADDRESS) {
//                handlerHelper.updateInteractiveMode(Mode.NONE, input);
//                return new AddressHandler()
//                        .getAddressMatchResponse(input);
//            } else if (handlerHelper.getInteractiveMode() == Mode.ADDRESS_MATCH) {
//                handlerHelper.updateInteractiveMode(Mode.NONE, input);
//                return new AddressHandler()
//                        .getAddressStoreResponse(input);
//            } else if (handlerHelper.getInteractiveMode() == Mode.BINS) {
//                handlerHelper.updateInteractiveMode(Mode.NONE, input);
//                return new CollectionDateHandler().handle(input);
//            } else {
//                return Optional.empty(); // TODO: return error
//            }
//        } else if (input.matches(intentName("ThankYouIntent")) ||
//                input.matches(intentName("AMAZON.NoIntent")) ||
//                input.matches(intentName("AMAZON.StopIntent"))) {
//            return new StandardResponseHandler()
//                    .getStopResponse();
//        }
//
//        return Optional.empty();

    }
}
