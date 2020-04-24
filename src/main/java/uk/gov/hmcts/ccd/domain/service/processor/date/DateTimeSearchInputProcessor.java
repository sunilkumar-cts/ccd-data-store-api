package uk.gov.hmcts.ccd.domain.service.processor.date;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.primitives.Ints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.ccd.data.casedetails.search.MetaData;
import uk.gov.hmcts.ccd.domain.model.aggregated.CommonField;
import uk.gov.hmcts.ccd.domain.model.common.CommonDCPModel;
import uk.gov.hmcts.ccd.domain.model.definition.FieldType;
import uk.gov.hmcts.ccd.domain.model.search.CriteriaInput;
import uk.gov.hmcts.ccd.domain.model.search.CriteriaType;
import uk.gov.hmcts.ccd.domain.service.aggregated.DefaultGetCriteriaOperation;
import uk.gov.hmcts.ccd.domain.service.aggregated.GetCriteriaOperation;
import uk.gov.hmcts.ccd.endpoint.exceptions.DataProcessingException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static uk.gov.hmcts.ccd.domain.model.common.DisplayContextParameterType.DATETIMEENTRY;
import static uk.gov.hmcts.ccd.domain.model.definition.FieldType.*;
import static uk.gov.hmcts.ccd.domain.service.processor.date.DateTimeFormatParser.DATE_FORMAT;
import static uk.gov.hmcts.ccd.domain.service.processor.date.DateTimeFormatParser.DATE_TIME_FORMAT;

@Component
public class DateTimeSearchInputProcessor {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    private final DateTimeFormatParser dateTimeFormatParser;
    private final GetCriteriaOperation getCriteriaOperation;

    @Autowired
    public DateTimeSearchInputProcessor(final DateTimeFormatParser dateTimeFormatParser,
                                        @Qualifier(DefaultGetCriteriaOperation.QUALIFIER) final GetCriteriaOperation getCriteriaOperation) {
        this.dateTimeFormatParser = dateTimeFormatParser;
        this.getCriteriaOperation = getCriteriaOperation;
    }

    public Map<String, String> execute(String view, MetaData metadata, Map<String, String> queryParameters) {
        final List<? extends CriteriaInput> criteriaInputs = getCriteriaInputs(view, metadata);

        Map<String, String> newParams = new HashMap<>();
        queryParameters.entrySet().forEach(entry -> {
            final Optional<? extends CriteriaInput> input = findCriteriaInputField(criteriaInputs, entry.getKey().split("\\.")[0]);

            if (input.isPresent()) {
                if (isComplexPath(entry.getKey()) && input.get().getDisplayContextParameters().isEmpty()) {
                    handleNested(entry.getKey(), entry.getValue(), input.get(), newParams);
                } else {
                    handleTopLevel(entry.getKey(), entry.getValue(), input.get(), newParams);
                }
            } else {
                newParams.put(entry.getKey(), entry.getValue());
            }
        });

        return newParams;
    }

    private Optional<? extends CriteriaInput> findCriteriaInputField(List<? extends CriteriaInput> criteriaInputs, String fieldId) {
        return criteriaInputs.stream()
            .filter(i -> i.getField().getId().equals(fieldId))
            .findAny();
    }

    private List<? extends CriteriaInput> getCriteriaInputs(String view, MetaData metadata) {
        return getCriteriaOperation.execute(metadata.getCaseTypeId(), null,
            view == null ? CriteriaType.SEARCH : CriteriaType.valueOf(view));
    }

    private void handleTopLevel(String fieldPath, String queryValue, CriteriaInput criteriaInput, Map<String, String> newParams) {
        if (criteriaInput.hasDisplayContextParameter(DATETIMEENTRY)) {
            newParams.put(fieldPath,
                processValue(fieldPath, criteriaInput, queryValue, criteriaInput.getField().getType()));
        } else {
            newParams.put(fieldPath, queryValue);
        }
    }

    private void handleNested(String fieldPath, String queryValue, CriteriaInput criteriaInput, Map<String, String> newParams) {
        final Optional<CommonField> field = criteriaInput.getField().getType().getNestedField(fieldPath, true);

        if (field.isPresent() && field.get().hasDisplayContextParameter(DATETIMEENTRY)) {
            newParams.put(fieldPath,
                processValue(fieldPath, field.get(), queryValue, field.get().getFieldType()));
        } else {
            newParams.put(fieldPath, queryValue);
        }
    }

    private boolean isComplexPath(String path) {
        final String[] splitPath = path.split("\\.");
        return splitPath.length > 1 && Ints.tryParse(splitPath[1]) == null;
    }

    private String processValue(String id, CommonDCPModel dcpObject, String value, FieldType fieldType) {
        try {
            if (fieldType.getType().equals(DATE)) {
                return dateTimeFormatParser.convertDateToIso8601(format(dcpObject, fieldType), value);
            } else if (fieldType.getType().equals(DATETIME)) {
                return dateTimeFormatParser.convertDateTimeToIso8601(format(dcpObject, fieldType), value);
            } else if (fieldType.getType().equals(COLLECTION)) {
                return processValue(id, dcpObject, value, fieldType.getCollectionFieldType());
            } else {
                return value;
            }
        } catch (Exception e) {
            throw new DataProcessingException().withDetails(
                String.format("Unable to process search input %s with value %s. Expected format: %s",
                    id,
                    value,
                    format(dcpObject, fieldType))
            );
        }
    }

    private String format(CommonDCPModel dcpObject, FieldType fieldType) {
        return dcpObject.getDisplayContextParameterValue(DATETIMEENTRY)
            .orElseGet(() -> fieldType.getType().equals(DATE) ? DATE_FORMAT : DATE_TIME_FORMAT);
    }
}