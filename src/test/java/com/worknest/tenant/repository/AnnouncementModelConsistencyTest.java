package com.worknest.tenant.repository;

import com.worknest.tenant.entity.Announcement;
import jakarta.persistence.Column;
import jakarta.persistence.JoinColumn;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementModelConsistencyTest {

    private static final Set<String> ENTITY_FIELDS = Set.of(
            "id",
            "title",
            "content",
            "legacyMessage",
            "createdBy",
            "createdByName",
            "createdByRole",
            "pinned",
            "team",
            "createdAt",
            "updatedAt");

    private static final Set<String> REMOVED_COLUMN_NAMES = Set.of(
            "deleted",
            "deleted_at",
            "department",
            "priority",
            "status",
            "publish_date",
            "expiry_date",
            "announcement_type",
            "visibility",
            "target_audience",
            "target_role",
            "specific_employee_id",
            "last_updated_by_id",
            "read_count",
            "announcement_reads");

    @Test
    void entityContainsOnlyTheSimplifiedPersistedModel() {
        Set<String> declaredFields = Arrays.stream(Announcement.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertThat(declaredFields).isEqualTo(ENTITY_FIELDS);
    }

    @Test
    void mappingsAndRepositoryMetadataDoNotReferenceRemovedColumns() {
        Set<String> mappedColumns = Arrays.stream(Announcement.class.getDeclaredFields())
                .flatMap(field -> {
                    Column column = field.getAnnotation(Column.class);
                    JoinColumn joinColumn = field.getAnnotation(JoinColumn.class);
                    return java.util.stream.Stream.of(
                                    column == null ? null : column.name(),
                                    joinColumn == null ? null : joinColumn.name())
                            .filter(value -> value != null && !value.isBlank());
                })
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        assertThat(mappedColumns).doesNotContainAnyElementsOf(REMOVED_COLUMN_NAMES);

        for (Method method : AnnouncementRepository.class.getDeclaredMethods()) {
            Query query = method.getAnnotation(Query.class);
            if (query != null) {
                assertThat(query.value().toLowerCase())
                        .doesNotContain(REMOVED_COLUMN_NAMES.toArray(String[]::new));
                assertThat(query.countQuery().toLowerCase())
                        .doesNotContain(REMOVED_COLUMN_NAMES.toArray(String[]::new));
            }
            EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);
            if (entityGraph != null) {
                assertThat(entityGraph.attributePaths())
                        .allSatisfy(path ->
                                assertThat(path.split("\\.")[0])
                                        .isIn(ENTITY_FIELDS));
            }
        }
    }
}
