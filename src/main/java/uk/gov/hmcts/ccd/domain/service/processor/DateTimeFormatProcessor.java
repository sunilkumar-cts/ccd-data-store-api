package uk.gov.hmcts.ccd.domain.service.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.domain.model.aggregated.CaseViewFieldBuilder;
import uk.gov.hmcts.ccd.domain.model.aggregated.CommonField;
import uk.gov.hmcts.ccd.domain.types.BaseType;
import uk.gov.hmcts.ccd.domain.types.CollectionValidator;
import uk.gov.hmcts.ccd.endpoint.exceptions.DataProcessingException;

import java.util.Arrays;
import java.util.List;

import static uk.gov.hmcts.ccd.domain.model.definition.FieldType.*;
import static uk.gov.hmcts.ccd.domain.service.processor.DisplayContextParameter.*;

@Component
public class DateTimeFormatProcessor extends FieldProcessor {

    private static final List<String> SUPPORTED_TYPES = Arrays.asList(DATETIME, DATE);

    private final DateTimeFormatParser dateTimeFormatParser;

    @Autowired
    public DateTimeFormatProcessor(CaseViewFieldBuilder caseViewFieldBuilder,
                                   DateTimeFormatParser dateTimeFormatParser) {
        super(caseViewFieldBuilder);
        this.dateTimeFormatParser = dateTimeFormatParser;
    }

    @Override
    protected JsonNode executeSimple(JsonNode node, CommonField field, BaseType baseType, String fieldPath) {
        return !isNullOrEmpty(node)
            && hasDisplayContextParameterType(field.getDisplayContextParameter(), DisplayContextParameterType.DATETIMEENTRY)
            && isSupportedBaseType(baseType) ?
            createNode(field.getDisplayContextParameter(), node.asText(), baseType, fieldPath) :
            node;
    }

    @Override
    protected JsonNode executeCollection(JsonNode collectionNode, CommonField caseViewField, String fieldPath) {
        final BaseType collectionFieldType = BaseType.get(caseViewField.getFieldType().getCollectionFieldType().getType());

        if (hasDisplayContextParameterType(caseViewField.getDisplayContextParameter(), DisplayContextParameterType.DATETIMEENTRY)
            && isSupportedBaseType(collectionFieldType)) {
            ArrayNode newNode = MAPPER.createArrayNode();
            collectionNode.forEach(item -> {
                JsonNode newItem = item.deepCopy();
                ((ObjectNode)newItem).replace(CollectionValidator.VALUE,
                    createNode(caseViewField.getDisplayContextParameter(), item.get(CollectionValidator.VALUE).asText(), collectionFieldType, fieldPath));
                newNode.add(newItem);
            });

            return newNode;
        }

        return collectionNode;
    }

    private boolean isSupportedBaseType(BaseType baseType) {
        return SUPPORTED_TYPES.contains(baseType.getType());
    }

    private TextNode createNode(String displayContextParameter, String valueToConvert, BaseType baseType, String fieldPath) {
        String format = getDisplayContextParameterOfType(displayContextParameter, DisplayContextParameterType.DATETIMEENTRY)
            .map(DisplayContextParameter::getValue)
            .orElse("");
        try {
            if (baseType == BaseType.get(DATETIME)) {
                return new TextNode(dateTimeFormatParser.convertDateTimeToIso8601(format, valueToConvert));
            } else {
                return new TextNode(dateTimeFormatParser.convertDateToIso8601(format, valueToConvert));
            }
        } catch (Exception e) {
            throw new DataProcessingException().withDetails(
                String.format("Unable to process field %s with value %s. Expected format: %s",
                    fieldPath,
                    valueToConvert,
                    format)
            );
        }
    }
}