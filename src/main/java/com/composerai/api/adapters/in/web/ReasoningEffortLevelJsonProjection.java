package com.composerai.api.adapters.in.web;

import com.composerai.api.domain.model.ReasoningEffortLevel;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import org.springframework.boot.jackson.JsonComponent;

/** Projects the framework-free reasoning vocabulary onto Composer's JSON boundary. */
@JsonComponent
public final class ReasoningEffortLevelJsonProjection {

    private ReasoningEffortLevelJsonProjection() {}

    public static final class Serializer extends JsonSerializer<ReasoningEffortLevel> {

        @Override
        public void serialize(
                ReasoningEffortLevel reasoningEffort,
                JsonGenerator jsonGenerator,
                SerializerProvider serializerProvider)
                throws IOException {
            jsonGenerator.writeString(reasoningEffort.externalName());
        }
    }

    public static final class Deserializer extends JsonDeserializer<ReasoningEffortLevel> {

        @Override
        public ReasoningEffortLevel deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                throws IOException {
            String effortName = jsonParser.getValueAsString();
            if (effortName == null) {
                return (ReasoningEffortLevel)
                        deserializationContext.handleUnexpectedToken(ReasoningEffortLevel.class, jsonParser);
            }
            try {
                return ReasoningEffortLevel.parse(effortName);
            } catch (IllegalArgumentException invalidEffort) {
                return (ReasoningEffortLevel) deserializationContext.handleWeirdStringValue(
                        ReasoningEffortLevel.class, effortName, invalidEffort.getMessage());
            }
        }
    }
}
