package com.banquemisr.copilot.servicerequest;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RequestDiff {

    public record Content(String title, String description, RequestPayload payload) {
    }

    private RequestDiff() {
    }

    public static List<FieldChange> between(Content before, Content after) {
        Map<String, Object> from = flatten(before);
        Map<String, Object> to = flatten(after);
        Set<String> fields = new LinkedHashSet<>(from.keySet());
        fields.addAll(to.keySet());

        List<FieldChange> changes = new ArrayList<>();
        for (String field : fields) {
            Object oldValue = from.get(field);
            Object newValue = to.get(field);
            if (!Objects.equals(oldValue, newValue)) {
                changes.add(new FieldChange(field, oldValue, newValue));
            }
        }
        return changes;
    }

    private static Map<String, Object> flatten(Content content) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("title", normalize(content.title()));
        values.put("description", normalize(content.description()));
        for (RecordComponent component : content.payload().getClass().getRecordComponents()) {
            values.put("payload." + component.getName(), normalize(read(component, content.payload())));
        }
        return values;
    }

    private static Object read(RecordComponent component, RequestPayload payload) {
        try {
            return component.getAccessor().invoke(payload);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Cannot read " + component.getName(), ex);
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof String text) {
            return text.isBlank() ? null : text.trim();
        }
        if (value instanceof Collection<?> items) {
            return items.isEmpty() ? null : List.copyOf(items);
        }
        return value;
    }
}
