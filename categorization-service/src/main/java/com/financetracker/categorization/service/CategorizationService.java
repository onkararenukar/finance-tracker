package com.financetracker.categorization.service;

import com.financetracker.categorization.dto.AssignCategoryRequest;
import com.financetracker.categorization.entity.Category;
import com.financetracker.categorization.entity.CategorizationStatus;
import com.financetracker.categorization.entity.TransactionCategory;
import com.financetracker.categorization.repository.CategoryRepository;
import com.financetracker.categorization.repository.TransactionCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Categorizes one transaction: asks the LLM for a category name +
 * one-line description, then checks whether that name matches an
 * existing category (case-insensitively).
 *
 * <ul>
 *   <li><b>Match found</b> -&gt; status = CATEGORIZED, category_id set
 *       immediately, no human involvement needed.</li>
 *   <li><b>No match</b> -&gt; status = PENDING_REVIEW, category_id left
 *       NULL, suggested_category_name holds the LLM's proposal. We do
 *       NOT auto-create the new category - that decision is explicitly
 *       yours, via {@code TransactionCategoryController.assignCategory}.
 *       This is the exact behavior you asked for: "if found something
 *       new in the category let me choose which category... belongs to."</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CategorizationService {

    private final LlmCategorizationClient llmCategorizationClient;
    private final CategoryRepository categoryRepository;
    private final TransactionCategoryRepository transactionCategoryRepository;

    public void categorize(Long transactionId, String bankName, String description,
                            BigDecimal amount, String direction) {

        var existingCategories = categoryRepository.findAllByOrderByNameAsc();
        var existingNames = existingCategories.stream().map(c -> c.getName()).toList();

        var llmResult = llmCategorizationClient.categorize(bankName, description, amount, direction, existingNames);

        var matched = categoryRepository.findByNameIgnoreCase(llmResult.categoryName());

        TransactionCategory record;
        if (matched.isPresent()) {
            record = new TransactionCategory(
                    transactionId,
                    matched.get().getId(),
                    CategorizationStatus.CATEGORIZED,
                    llmResult.categoryName(),
                    llmResult.oneLineDescription(),
                    llmCategorizationClient.modelName()
            );
            log.info("Transaction id={} categorized as '{}' (matched existing category id={})",
                    transactionId, matched.get().getName(), matched.get().getId());
        } else {
            record = new TransactionCategory(
                    transactionId,
                    null,
                    CategorizationStatus.PENDING_REVIEW,
                    llmResult.categoryName(),
                    llmResult.oneLineDescription(),
                    llmCategorizationClient.modelName()
            );
            log.info("Transaction id={} - LLM suggested NEW category '{}', flagged for review",
                    transactionId, llmResult.categoryName());
        }

        transactionCategoryRepository.save(record);
    }

    /**
     * Resolves a PENDING_REVIEW item per your decision: either "use this
     * existing category instead" or "yes, actually create this new one."
     *
     * <p>Deliberately the ONLY place a new {@link Category} row is ever
     * created from a suggestion - {@link #categorize} above never does
     * this automatically, exactly per the requirement that new
     * categories always go through your explicit confirmation.
     */
    public void assignCategory(Long transactionId, AssignCategoryRequest request) {
        TransactionCategory record = transactionCategoryRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No categorization record found for transaction id=" + transactionId));

        Long resolvedCategoryId;
        if (request.categoryId() != null) {
            Category existing = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No category found with id=" + request.categoryId()));
            resolvedCategoryId = existing.getId();
            log.info("Transaction id={} manually assigned to existing category '{}'",
                    transactionId, existing.getName());
        } else {
            // Confirming a genuinely new category - created here, and
            // only here, as a direct result of your explicit choice.
            Category created = categoryRepository.save(new Category(request.newCategoryName(), false));
            resolvedCategoryId = created.getId();
            log.info("Transaction id={} confirmed a NEW category '{}' (id={})",
                    transactionId, created.getName(), created.getId());
        }

        record.setCategoryId(resolvedCategoryId);
        record.setStatus(CategorizationStatus.CATEGORIZED);
        transactionCategoryRepository.save(record);
    }
}
