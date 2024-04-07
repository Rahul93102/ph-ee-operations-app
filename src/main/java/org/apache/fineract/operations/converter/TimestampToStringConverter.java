package org.apache.fineract.operations.converter;

import org.apache.fineract.operations.Task_;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Date;

import static org.apache.fineract.core.service.OperatorUtils.formatUtcDate;

public class TimestampToStringConverter implements Converter<Long, String> {

    @Override
    public String convert(MappingContext<Long, String> mappingContext) {
        if (mappingContext.getMapping().getLastDestinationProperty().getName().equals(Task_.timestamp.getName())) {
            return formatUtcDate(new Date(mappingContext.getSource()));
        }
        return null;
    }
}
