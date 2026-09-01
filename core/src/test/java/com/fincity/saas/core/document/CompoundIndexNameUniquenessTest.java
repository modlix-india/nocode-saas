package com.fincity.saas.core.document;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Two @CompoundIndex annotations on one document that share a name are silently
 * broken: Mongo rejects the second with IndexKeySpecsConflict, so it is never
 * created and every query relying on it runs unindexed. Nothing fails loudly, so
 * it can sit there for years.
 *
 * Two documents were in exactly this state when this test was written:
 *
 *   Connection    both indexes named "connectionFilteringIndex"
 *   Notification  both named "notificationFilteringIndex", and both unique, so a
 *                 uniqueness constraint that reads as enforced never was
 *
 * This is a plain reflection test rather than an assertion against a live Mongo,
 * deliberately: index creation happens once when a collection is first resolved,
 * and the integration harness drops collections between tests, so a live check
 * would pass or fail depending on test ordering.
 */
@DisplayName("Mongo document index declarations")
class CompoundIndexNameUniquenessTest {

    private static final String BASE_PACKAGE = "com.fincity";

    @Test
    @DisplayName("no document declares two compound indexes under the same name")
    void compoundIndexNamesAreUniquePerDocument() throws ClassNotFoundException {

        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Document.class));

        List<String> offenders = new ArrayList<>();

        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {

            Class<?> type = Class.forName(definition.getBeanClassName());

            List<CompoundIndex> indexes = new ArrayList<>();
            CompoundIndexes container = type.getAnnotation(CompoundIndexes.class);
            if (container != null)
                indexes.addAll(List.of(container.value()));
            else {
                CompoundIndex single = type.getAnnotation(CompoundIndex.class);
                if (single != null)
                    indexes.add(single);
            }

            Set<String> seen = new HashSet<>();
            for (CompoundIndex index : indexes) {
                if (index.name().isEmpty())
                    continue;
                if (!seen.add(index.name()))
                    offenders.add(type.getName() + " declares '" + index.name() + "' more than once");
            }
        }

        assertTrue(offenders.isEmpty(),
                "Compound index names must be unique within a document, otherwise Mongo rejects all but "
                        + "the first with IndexKeySpecsConflict and the rest are never created:\n  "
                        + String.join("\n  ", offenders));
    }
}
