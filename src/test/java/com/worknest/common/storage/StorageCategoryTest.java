package com.worknest.common.storage;

import com.worknest.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageCategoryTest {
    @Test
    void mapsLegacyFoldersToTheCentralCategoryModel() {
        assertThat(StorageCategory.fromClientValue(null, "projects/documents", "doc"))
                .isEqualTo(StorageCategory.PROJECT_ATTACHMENT);
        assertThat(StorageCategory.fromClientValue(null, "chat/attachments", "doc"))
                .isEqualTo(StorageCategory.CHAT_ATTACHMENT);
        assertThat(StorageCategory.fromClientValue("candidate-resume", null, null))
                .isEqualTo(StorageCategory.CANDIDATE_RESUME);
    }

    @Test
    void rejectsUnknownClientCategories() {
        assertThatThrownBy(() -> StorageCategory.fromClientValue("arbitrary", null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported storage category");
    }
}
