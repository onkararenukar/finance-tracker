package com.financetracker.categorization.service;

import com.financetracker.categorization.dto.AssignCategoryRequest;
import com.financetracker.categorization.dto.LlmCategorization;
import com.financetracker.categorization.entity.Category;
import com.financetracker.categorization.entity.CategorizationStatus;
import com.financetracker.categorization.entity.TransactionCategory;
import com.financetracker.categorization.repository.CategoryRepository;
import com.financetracker.categorization.repository.TransactionCategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategorizationServiceTest {

    private LlmCategorizationClient llmClient;
    private CategoryRepository categoryRepository;
    private TransactionCategoryRepository transactionCategoryRepository;
    private CategorizationService service;

    @BeforeEach
    void setUp() {
        llmClient = mock(LlmCategorizationClient.class);
        categoryRepository = mock(CategoryRepository.class);
        transactionCategoryRepository = mock(TransactionCategoryRepository.class);
        service = new CategorizationService(llmClient, categoryRepository, transactionCategoryRepository);

        when(categoryRepository.findAllByOrderByNameAsc())
                .thenReturn(List.of(new Category("Food & Dining", true)));
        when(llmClient.modelName()).thenReturn("test-model");
    }

    @Test
    void categorize_llmSuggestsExistingCategory_marksAsCategorized() {
        when(llmClient.categorize(any(), any(), any(), any(), any()))
                .thenReturn(new LlmCategorization("Food & Dining", "Lunch at a cafe"));

        var existing = new Category("Food & Dining", true);
        existing.setId(1L);
        when(categoryRepository.findByNameIgnoreCase("Food & Dining")).thenReturn(Optional.of(existing));

        service.categorize(100L, "Test Bank", "SWIGGY ORDER", BigDecimal.TEN, "DEBIT");

        ArgumentCaptor<TransactionCategory> captor = ArgumentCaptor.forClass(TransactionCategory.class);
        verify(transactionCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CategorizationStatus.CATEGORIZED);
        assertThat(captor.getValue().getCategoryId()).isEqualTo(1L);
    }

    @Test
    void categorize_llmSuggestsUnknownCategory_marksAsPendingReview() {
        when(llmClient.categorize(any(), any(), any(), any(), any()))
                .thenReturn(new LlmCategorization("Pet Care", "Vet visit for the dog"));
        when(categoryRepository.findByNameIgnoreCase("Pet Care")).thenReturn(Optional.empty());

        service.categorize(101L, "Test Bank", "VET CLINIC", BigDecimal.valueOf(50), "DEBIT");

        ArgumentCaptor<TransactionCategory> captor = ArgumentCaptor.forClass(TransactionCategory.class);
        verify(transactionCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CategorizationStatus.PENDING_REVIEW);
        assertThat(captor.getValue().getCategoryId()).isNull();
        assertThat(captor.getValue().getSuggestedCategoryName()).isEqualTo("Pet Care");
    }

    @Test
    void assignCategory_withNewCategoryName_createsCategoryAndResolves() {
        var pending = new TransactionCategory(101L, null, CategorizationStatus.PENDING_REVIEW,
                "Pet Care", "Vet visit", "test-model");
        when(transactionCategoryRepository.findById(101L)).thenReturn(Optional.of(pending));

        var created = new Category("Pet Care", false);
        created.setId(99L);
        when(categoryRepository.save(any())).thenReturn(created);

        service.assignCategory(101L, new AssignCategoryRequest(null, "Pet Care"));

        verify(categoryRepository).save(argThat(c -> c.getName().equals("Pet Care") && !c.isSeeded()));
        assertThat(pending.getStatus()).isEqualTo(CategorizationStatus.CATEGORIZED);
        assertThat(pending.getCategoryId()).isEqualTo(99L);
    }

    @Test
    void assignCategory_withExistingCategoryId_resolvesWithoutCreatingNewCategory() {
        var pending = new TransactionCategory(102L, null, CategorizationStatus.PENDING_REVIEW,
                "Pet Care", "Vet visit", "test-model");
        when(transactionCategoryRepository.findById(102L)).thenReturn(Optional.of(pending));

        var existing = new Category("Health & Wellness", true);
        existing.setId(5L);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(existing));

        service.assignCategory(102L, new AssignCategoryRequest(5L, null));

        verify(categoryRepository, never()).save(any());
        assertThat(pending.getStatus()).isEqualTo(CategorizationStatus.CATEGORIZED);
        assertThat(pending.getCategoryId()).isEqualTo(5L);
    }

    @Test
    void assignCategory_transactionNotFound_throwsException() {
        when(transactionCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.assignCategory(999L, new AssignCategoryRequest(1L, null))
        );
    }

    @Test
    void assignCategory_bothCategoryIdAndNewCategoryNameProvided_throwsException() {
        var pending = new TransactionCategory(102L, null, CategorizationStatus.PENDING_REVIEW,
                "Pet Care", "Vet visit", "test-model");
        when(transactionCategoryRepository.findById(102L)).thenReturn(Optional.of(pending));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.assignCategory(102L, new AssignCategoryRequest(1L, "New Category"))
        );
    }

    @Test
    void assignCategory_neitherCategoryIdNorNewCategoryNameProvided_throwsException() {
        var pending = new TransactionCategory(102L, null, CategorizationStatus.PENDING_REVIEW,
                "Pet Care", "Vet visit", "test-model");
        when(transactionCategoryRepository.findById(102L)).thenReturn(Optional.of(pending));

        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> service.assignCategory(102L, new AssignCategoryRequest(null, null))
        );
    }

    @Test
    void categorize_llmReturnsNullDescription_usesLLMDescription() {
        when(llmClient.categorize(any(), any(), any(), any(), any()))
                .thenReturn(new LlmCategorization("Food & Dining", null));
        
        var existing = new Category("Food & Dining", true);
        existing.setId(1L);
        when(categoryRepository.findByNameIgnoreCase("Food & Dining")).thenReturn(Optional.of(existing));

        service.categorize(100L, "Test Bank", "SWIGGY ORDER", BigDecimal.TEN, "DEBIT");

        ArgumentCaptor<TransactionCategory> captor = ArgumentCaptor.forClass(TransactionCategory.class);
        verify(transactionCategoryRepository).save(captor.capture());
        // The implementation uses whatever the LLM returns, even if null
        assertThat(captor.getValue().getOneLineDescription()).isNull();
    }

    @Test
    void categorize_caseInsensitiveCategoryMatching() {
        when(llmClient.categorize(any(), any(), any(), any(), any()))
                .thenReturn(new LlmCategorization("food & dining", "Lunch at a cafe"));

        var existing = new Category("Food & Dining", true);
        existing.setId(1L);
        when(categoryRepository.findByNameIgnoreCase("food & dining")).thenReturn(Optional.of(existing));

        service.categorize(100L, "Test Bank", "SWIGGY ORDER", BigDecimal.TEN, "DEBIT");

        ArgumentCaptor<TransactionCategory> captor = ArgumentCaptor.forClass(TransactionCategory.class);
        verify(transactionCategoryRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(CategorizationStatus.CATEGORIZED);
        assertThat(captor.getValue().getCategoryId()).isEqualTo(1L);
    }
}
