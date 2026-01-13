package pt.psoft.g1.psoftg1.bookmanagement.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.psoft.g1.psoftg1.bookmanagement.model.Book;
import pt.psoft.g1.psoftg1.bookmanagement.services.BookService;
import pt.psoft.g1.psoftg1.bookmanagement.services.CreateBookRequest;
import pt.psoft.g1.psoftg1.configuration.FeatureFlagConfig;
import pt.psoft.g1.psoftg1.shared.annotations.FeatureFlag;
import pt.psoft.g1.psoftg1.shared.services.DarkLaunchService;
import pt.psoft.g1.psoftg1.shared.services.KillSwitchService;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dark Launch Controller for Book Management
 * 
 * This controller demonstrates:
 * - Dark Launch: New features tested in production without user exposure
 * - Kill Switch: Instant feature disabling
 * - Shadow Traffic: Compare old vs new implementations
 * 
 * Release Strategy Labels:
 * - release-strategy: dark-launch
 * - kill-switch-enabled: true
 */
@Tag(name = "Books (Dark Launch)", description = "Dark Launch features for Book Management")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books/experimental")
@Slf4j
public class BookControllerDarkLaunch {

    private final BookService bookService;
    private final BookViewMapper bookViewMapper;
    private final FeatureFlagConfig featureFlagConfig;
    private final DarkLaunchService darkLaunchService;
    private final KillSwitchService killSwitchService;

    // ============================================
    // DARK LAUNCH FEATURES
    // ============================================

    /**
     * AI-powered book recommendations (Dark Launch)
     * 
     * This feature is in dark launch mode:
     * - Only visible to allowed users
     * - Shadow traffic compares with basic recommendations
     * - Can be killed instantly if issues arise
     */
    @Operation(summary = "[DARK LAUNCH] Get AI-powered book recommendations")
    @GetMapping("/recommendations/{isbn}")
    @FeatureFlag(name = "book.recommendations", fallback = "getBasicRecommendations", darkLaunch = true)
    public ResponseEntity<RecommendationsResponse> getAIRecommendations(
            @PathVariable String isbn,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("[DARK LAUNCH] AI Recommendations requested for ISBN: {}", isbn);

        // Check if user is allowed for dark launch
        if (!isUserAllowedForDarkLaunch(userId)) {
            return getBasicRecommendations(isbn, userId);
        }

        // Execute with shadow traffic comparison
        List<BookView> recommendations = darkLaunchService.executeWithShadow(
                "book.recommendations",
                () -> generateAIRecommendations(isbn),
                () -> generateBasicRecommendations(isbn));

        return ResponseEntity.ok()
                .header("X-Feature-Flag", "book.recommendations")
                .header("X-Dark-Launch", "true")
                .header("X-Kill-Switch-Enabled", "true")
                .body(new RecommendationsResponse(
                        isbn,
                        recommendations,
                        "AI_POWERED",
                        "Recommendations generated using AI (Dark Launch)",
                        true));
    }

    /**
     * Fallback: Basic recommendations (when AI is disabled/killed)
     */
    public ResponseEntity<RecommendationsResponse> getBasicRecommendations(
            String isbn, String userId) {

        log.info("[FALLBACK] Basic Recommendations for ISBN: {}", isbn);

        List<BookView> recommendations = generateBasicRecommendations(isbn);

        return ResponseEntity.ok()
                .header("X-Feature-Flag", "book.recommendations")
                .header("X-Fallback", "true")
                .body(new RecommendationsResponse(
                        isbn,
                        recommendations,
                        "BASIC",
                        "Basic recommendations (AI feature disabled)",
                        false));
    }

    /**
     * Book analytics (Dark Launch)
     */
    @Operation(summary = "[DARK LAUNCH] Get book analytics")
    @GetMapping("/analytics/{isbn}")
    @FeatureFlag(name = "book.analytics", fallback = "getBasicAnalytics", trackErrors = true)
    public ResponseEntity<AnalyticsResponse> getBookAnalytics(
            @PathVariable String isbn,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("[DARK LAUNCH] Analytics requested for ISBN: {}", isbn);

        // Simulated advanced analytics
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("isbn", isbn);
        analytics.put("readingTime", "4.5 hours");
        analytics.put("popularityScore", 8.7);
        analytics.put("sentimentAnalysis", "Positive");
        analytics.put("topKeywords", List.of("adventure", "mystery", "thriller"));
        analytics.put("readerDemographics", Map.of(
                "18-25", 35,
                "26-35", 40,
                "36-50", 20,
                "50+", 5));

        return ResponseEntity.ok()
                .header("X-Feature-Flag", "book.analytics")
                .header("X-Dark-Launch", "true")
                .body(new AnalyticsResponse(isbn, analytics, "ADVANCED", true));
    }

    /**
     * Fallback: Basic analytics
     */
    public ResponseEntity<AnalyticsResponse> getBasicAnalytics(String isbn, String userId) {
        Map<String, Object> analytics = new HashMap<>();
        analytics.put("isbn", isbn);
        analytics.put("status", "Basic analytics only");

        return ResponseEntity.ok(new AnalyticsResponse(isbn, analytics, "BASIC", false));
    }

    /**
     * AI Summary generation (Dark Launch with Kill Switch)
     */
    @Operation(summary = "[DARK LAUNCH] Generate AI summary for a book")
    @PostMapping("/ai-summary/{isbn}")
    @FeatureFlag(name = "book.ai-summary", trackErrors = true)
    public ResponseEntity<AISummaryResponse> generateAISummary(
            @PathVariable String isbn,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        // Check kill switch
        if (killSwitchService.isFeatureKilled("book.ai-summary")) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .header("X-Kill-Switch", "ACTIVE")
                    .body(new AISummaryResponse(
                            isbn,
                            null,
                            "KILLED",
                            "AI Summary feature is currently disabled by kill switch"));
        }

        log.info("[DARK LAUNCH] AI Summary generation for ISBN: {}", isbn);

        try {
            // Simulated AI summary generation
            String summary = "This is an AI-generated summary of the book. " +
                    "The story follows the protagonist through a series of adventures...";

            return ResponseEntity.ok()
                    .header("X-Feature-Flag", "book.ai-summary")
                    .header("X-Dark-Launch", "true")
                    .body(new AISummaryResponse(isbn, summary, "SUCCESS", null));

        } catch (Exception e) {
            // Report error for auto-kill tracking
            killSwitchService.reportError("book.ai-summary", e.getMessage());
            throw e;
        }
    }

    /**
     * Batch import (Dark Launch with Kill Switch)
     */
    @Operation(summary = "[DARK LAUNCH] Batch import books")
    @PostMapping("/batch-import")
    @FeatureFlag(name = "book.batch-import", trackErrors = true)
    public ResponseEntity<BatchImportResponse> batchImport(
            @RequestBody List<CreateBookRequest> books,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        log.info("[DARK LAUNCH] Batch import requested: {} books", books.size());

        List<String> imported = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (CreateBookRequest request : books) {
            try {
                String isbn = "978-" + System.currentTimeMillis();
                bookService.create(request, isbn);
                imported.add(isbn);
            } catch (Exception e) {
                failed.add(request.getTitle() + ": " + e.getMessage());
                killSwitchService.reportError("book.batch-import", e.getMessage());
            }
        }

        return ResponseEntity.ok()
                .header("X-Feature-Flag", "book.batch-import")
                .header("X-Dark-Launch", "true")
                .body(new BatchImportResponse(
                        imported.size(),
                        failed.size(),
                        imported,
                        failed));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    private boolean isUserAllowedForDarkLaunch(String userId) {
        if (userId == null) {
            return false;
        }
        return darkLaunchService.isUserAllowedForDarkLaunch(userId, null);
    }

    private List<BookView> generateAIRecommendations(String isbn) {
        // Simulated AI-powered recommendations
        // In production, this would call an AI service
        return bookService.findByGenre("Fiction").stream()
                .limit(5)
                .map(bookViewMapper::toBookView)
                .collect(Collectors.toList());
    }

    private List<BookView> generateBasicRecommendations(String isbn) {
        // Basic recommendations based on genre
        try {
            Book book = bookService.findByIsbn(isbn);
            return bookService.findByGenre(book.getGenre().toString()).stream()
                    .filter(b -> !b.getIsbn().equals(isbn))
                    .limit(3)
                    .map(bookViewMapper::toBookView)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    // ============================================
    // DTOs
    // ============================================

    public record RecommendationsResponse(
            String isbn,
            List<BookView> recommendations,
            String algorithm,
            String message,
            boolean darkLaunch) {
    }

    public record AnalyticsResponse(
            String isbn,
            Map<String, Object> analytics,
            String type,
            boolean darkLaunch) {
    }

    public record AISummaryResponse(
            String isbn,
            String summary,
            String status,
            String error) {
    }

    public record BatchImportResponse(
            int successCount,
            int failureCount,
            List<String> importedIsbns,
            List<String> errors) {
    }
}
