package uk.gov.hmcts.ccd.domain.types;

import com.fasterxml.jackson.databind.JsonNode;
import uk.gov.hmcts.ccd.domain.model.definition.CaseField;

import javax.inject.Named;
import javax.inject.Singleton;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;

import static java.time.format.DateTimeFormatter.ISO_DATE;
import static java.time.format.DateTimeFormatter.ISO_DATE_TIME;
import static uk.gov.hmcts.ccd.domain.types.TextValidator.checkRegex;

/**
 * Max and Min is expressed as EPOCH
 */
@Named
@Singleton
public class DateTimeValidator implements BaseTypeValidator {
    private static final String TYPE_ID = "DateTime";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-mm-dd'T'HH:mm:ss.SSS");

    public BaseType getType() {
        return BaseType.get(TYPE_ID);
    }

    @Override
    public List<ValidationResult> validate(final String dataFieldId,
                                           final JsonNode dataValue,
                                           final CaseField caseFieldDefinition) {
        if (isNullOrEmpty(dataValue)) {
            return Collections.emptyList();
        }

        final LocalDateTime dateTimeValue;
        try {
            dateTimeValue = LocalDateTime.parse(dataValue.asText(), ISO_DATE_TIME);
        } catch (DateTimeParseException e) {
            return Collections.singletonList(new ValidationResult(dataValue + " is not a valid ISO 8601 date", dataFieldId));
        }

        if (!checkMax(caseFieldDefinition.getFieldType().getMax(), dateTimeValue)) {
            return Collections.singletonList(new ValidationResult("The date should be earlier than "
                + DATE_TIME_FORMATTER.format(epochTimeStampToLocalDate(caseFieldDefinition.getFieldType().getMax())), dataFieldId));
        }

        if (!checkMin(caseFieldDefinition.getFieldType().getMin(), dateTimeValue)) {
            return Collections.singletonList(new ValidationResult("The date should be later than "
                + DATE_TIME_FORMATTER.format(epochTimeStampToLocalDate(caseFieldDefinition.getFieldType().getMin())), dataFieldId));
        }

        if (!checkRegex(caseFieldDefinition.getFieldType().getRegularExpression(), dataValue.asText())) {
            return Collections.singletonList(new ValidationResult(dataValue.asText() + " Field Type Regex Failed:" + caseFieldDefinition.getFieldType().getRegularExpression(), dataFieldId));
        }

        if (!checkRegex(getType().getRegularExpression(), dataValue.asText())) {
            return Collections.singletonList(new ValidationResult(dataValue.asText() + " Date Type Regex Failed:" + getType().getRegularExpression(), dataFieldId));
        }

        return Collections.emptyList();
    }

    private Boolean checkMax(final BigDecimal max, final LocalDateTime dateValue) {
        if (max == null) {
            return true;
        }

        final Instant maxDate = Instant.ofEpochMilli(max.longValue());
        final Instant testDate = dateValue.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        return maxDate.isAfter(testDate) || maxDate.equals(testDate);
    }

    private Boolean checkMin(final BigDecimal min, final LocalDateTime dateValue) {
        if (min == null) {
            return true;
        }

        final Instant minDate = Instant.ofEpochMilli(min.longValue());
        final Instant testDate = dateValue.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        return minDate.isBefore(testDate) || minDate.equals(testDate);
    }

    private LocalDate epochTimeStampToLocalDate(final BigDecimal timestamp) {
        return Instant
            .ofEpochMilli(timestamp.longValue())
            .atZone(ZoneOffset.UTC)
            .toLocalDate();
    }
}
