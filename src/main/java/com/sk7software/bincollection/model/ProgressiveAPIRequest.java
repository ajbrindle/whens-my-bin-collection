package com.sk7software.bincollection.model;

public class ProgressiveAPIRequest {

    private Header header;
    private Directive directive;

    private static final String DIRECTIVE_TYPE = "VoicePlayer.Speak";

    public ProgressiveAPIRequest(){}

    public ProgressiveAPIRequest(String requestId, String speech) {
        header = new Header();
        header.setRequestId(requestId);

        directive = new Directive();
        directive.setType(DIRECTIVE_TYPE);
        directive.setSpeech(speech);
    }

    public Header getHeader() {
        return header;
    }

    public void setHeader(Header header) {
        this.header = header;
    }

    public Directive getDirective() {
        return directive;
    }

    public void setDirective(Directive directive) {
        this.directive = directive;
    }

    public class Header {
        private String requestId;

        public String getRequestId() {
            return requestId;
        }

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }
    }

    public class Directive {
        private String type;
        private String speech;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSpeech() {
            return speech;
        }

        public void setSpeech(String speech) {
            this.speech = speech;
        }
    }
}
