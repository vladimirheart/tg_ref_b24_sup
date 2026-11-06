package com.example.supportbot.max;

import com.example.supportbot.service.MessageSourceLink;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Only canonical Message.link is supported. Source identities never replace the outer sender or event identity. */
final class MaxQuestionnaireSourceLink {
    private MaxQuestionnaireSourceLink() {}

    static MessageSourceLink describe(JsonNode message) {
        JsonNode link=message.path("link");
        if(link.isMissingNode() || link.isNull())return null;
        if(!link.isObject() || !link.path("type").isTextual() || !link.path("message").isObject()
                || !link.path("message").path("mid").isTextual()
                || link.path("message").hasNonNull("body") || link.path("message").hasNonNull("link"))throw invalid();
        for(String alias:List.of("linked_message","forwarded_message","forward","source","link_type"))if(link.hasNonNull(alias))throw invalid();
        JsonNode author=link.path("sender");
        if(!author.isMissingNode() && !author.isNull() && !author.isObject())throw invalid();
        String user=null;
        if(author.hasNonNull("user_id")) {
            var id=author.get("user_id");
            if(!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue()<=0)throw invalid();
            user=id.asText();
        }
        String first=text(author,"first_name"),last=text(author,"last_name");
        String name=first==null?last:last==null?first:first+" "+last;
        if(name==null)name=text(author,"name");
        if(name==null)name=text(author,"username");
        return new MessageSourceLink(link.path("type").textValue(),link.path("message").path("mid").textValue(),user,name);
    }

    static JsonNode attachmentBody(JsonNode message,MessageSourceLink source) {
        JsonNode body=message.path("body");
        if(source!=null && "forward".equals(source.type())) {
            if(present(body.path("attachments")) || present(body.path("media")) || present(body.path("files")))throw invalid();
            body=message.path("link").path("message");
        }
        if(present(message.path("attachments")) || present(body.path("media")) || present(body.path("files")))throw invalid();
        return body;
    }
    private static boolean present(JsonNode value){return !value.isMissingNode() && !value.isNull() && (!value.isArray() || !value.isEmpty());}
    private static String text(JsonNode node,String field){var value=node.path(field);return value.isTextual()&&!value.textValue().isBlank()?value.textValue().trim():null;}
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Unsupported MAX message link");}
}
