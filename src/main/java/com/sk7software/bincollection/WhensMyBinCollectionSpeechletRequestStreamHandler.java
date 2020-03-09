package com.sk7software.bincollection;

import com.amazon.ask.Skill;
import com.amazon.ask.SkillStreamHandler;
import com.amazon.ask.Skills;
import com.amazon.ask.dispatcher.exception.ExceptionHandler;
import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.IntentRequest;
import com.amazon.ask.model.Response;
import com.amazon.ask.response.ResponseBuilder;
import com.sk7software.bincollection.handler.CollectionDateHandler;
import com.sk7software.bincollection.handler.LaunchRequestHandler;
import com.sk7software.bincollection.handler.PingRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;


/**
 * This class could be the handler for an AWS Lambda function powering an Alexa Skills Kit
 * experience. To do this, simply set the handler field in the AWS Lambda console to
 * "helloworld.JourneyTimerSpeechletRequestStreamHandler" For this to work, you'll also need to build
 * this project using the {@code lambda-compile} Ant task and upload the resulting zip file to power
 * your function.
 */
public class WhensMyBinCollectionSpeechletRequestStreamHandler extends SkillStreamHandler {
    private static final Logger log = LoggerFactory.getLogger(WhensMyBinCollectionSpeechletRequestStreamHandler.class);

    private static Skill getSkill() {
        return Skills.standard()
                .addRequestHandlers(
                        new LaunchRequestHandler(),
                        new CollectionDateHandler(),
                        new PingRequestHandler())
                .addExceptionHandler(
                        new ExceptionHandler() {
                            @Override
                            public boolean canHandle(HandlerInput handlerInput, Throwable throwable) {
                                return true;
                            }

                            @Override
                            public Optional<Response> handle(HandlerInput handlerInput, Throwable throwable) {
                                if (handlerInput.getRequestEnvelope().getRequest() instanceof IntentRequest) {
                                    IntentRequest ir = (IntentRequest)handlerInput.getRequestEnvelope().getRequest();
                                    log.debug("ERROR on intent: " + ir.getIntent().getName());
                                }
                                log.debug(throwable.getMessage());
                                return new ResponseBuilder()
                                        .withSpeech("There was an error looking up the collection dates")
                                        .withShouldEndSession(true)
                                        .build();
                            }
                        }
                )
                .withSkillId("amzn1.ask.skill.cd3c8ac4-75ca-4a0f-be29-3f39fcd62497")
                .build();
    }


    public WhensMyBinCollectionSpeechletRequestStreamHandler() {
        super(getSkill());
    }
}
