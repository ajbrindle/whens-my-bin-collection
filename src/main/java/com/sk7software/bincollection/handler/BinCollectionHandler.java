package com.sk7software.bincollection.handler;

import com.amazon.ask.dispatcher.request.handler.HandlerInput;
import com.amazon.ask.model.Response;

import java.util.Optional;

public abstract class BinCollectionHandler {
    protected HandlerHelper handlerHelper;
    protected HandlerInput input;

    public BinCollectionHandler(HandlerInput input) {
        this.handlerHelper = handlerHelper.getInstance(input);
        this.input = input;
    }

    public abstract Optional<Response> handle();
}
