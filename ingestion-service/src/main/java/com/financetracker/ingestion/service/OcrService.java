package com.financetracker.ingestion.service;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * OCR Service for extracting text from scanned PDFs and images.
 * 
 * This service handles cases where PDFs contain text stored as images
 * rather than selectable text, which is common with scanned bank statements.
 * It uses Tesseract OCR to convert images to machine-readable text.
 */
@Service
@Slf4j
public class OcrService {

    private final ITesseract tesseract;

    @Value("${finance-tracker.ocr.language:eng}")
    private String ocrLanguage;

    @Value("${finance-tracker.ocr.dpi:300}")
    private int ocrDpi;

    @Value("${finance-tracker.ocr.enabled:true}")
    private boolean ocrEnabled;

    public OcrService() {
        this.tesseract = new Tesseract();
    }

    /**
     * Extracts text from a PDF file using OCR if needed.
     * 
     * This method first attempts to extract text normally using PDFBox.
     * If the extracted text is empty or very short, it falls back to OCR
     * to handle scanned documents.
     *
     * @param filePath Path to the PDF file
     * @return Extracted text from the PDF
     * @throws IOException if there's an error reading the PDF
     */
    public String extractTextFromPdf(Path filePath) throws IOException {
        if (!ocrEnabled) {
            log.debug("OCR is disabled, attempting text extraction only");
            return extractTextWithPdfBox(filePath);
        }

        // First try normal text extraction
        String text = extractTextWithPdfBox(filePath);
        
        // If text extraction yields very little content, assume it's a scanned PDF
        if (text.trim().length() < 100) {
            log.info("PDF appears to be scanned ({} chars extracted), using OCR", text.trim().length());
            return extractTextWithOcr(filePath);
        }
        
        log.debug("PDF has sufficient text content ({} chars), skipping OCR", text.trim().length());
        return text;
    }

    /**
     * Extracts text using PDFBox's built-in text extraction.
     * This works for text-based PDFs with selectable text.
     */
    private String extractTextWithPdfBox(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            return stripper.getText(document);
        }
    }

    /**
     * Extracts text using OCR (Tesseract) for scanned PDFs.
     * Converts each page to an image and runs OCR on it.
     */
    private String extractTextWithOcr(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder fullText = new StringBuilder();
            
            // Configure Tesseract with OS-appropriate paths
            tesseract.setLanguage(ocrLanguage);
            
            // Set tessdata path based on OS
            String osName = System.getProperty("os.name").toLowerCase();
            if (osName.contains("mac") || osName.contains("darwin")) {
                // macOS path
                tesseract.setDatapath("/opt/homebrew/share/tessdata");
            } else if (osName.contains("linux")) {
                // Linux path
                tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
            } else if (osName.contains("win")) {
                // Windows path - let Tesseract use default
                // tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
            }
            
            tesseract.setOcrEngineMode(1); // LSTM OCR engine
            
            log.info("Starting OCR for {} pages", document.getNumberOfPages());
            
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                try {
                    // Render page as image at specified DPI
                    BufferedImage image = renderer.renderImageWithDPI(pageIndex, ocrDpi, ImageType.RGB);
                    
                    // Run OCR on the image
                    String pageText = tesseract.doOCR(image);
                    
                    fullText.append(pageText).append("\n\n");
                    log.debug("OCR completed for page {}/{}", pageIndex + 1, document.getNumberOfPages());
                    
                } catch (TesseractException e) {
                    log.error("OCR failed for page {}/{}", pageIndex + 1, document.getNumberOfPages(), e);
                    // Continue with other pages even if one fails
                }
            }
            
            String result = fullText.toString();
            log.info("OCR completed: {} characters extracted", result.length());
            return result;
        }
    }

    /**
     * Extracts text from a single image file.
     * Useful for processing individual statement pages saved as images.
     */
    public String extractTextFromImage(Path imagePath) throws IOException, TesseractException {
        if (!ocrEnabled) {
            throw new IllegalStateException("OCR is disabled but image processing was requested");
        }
        
        tesseract.setLanguage(ocrLanguage);
        
        // Set tessdata path based on OS
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            tesseract.setDatapath("/opt/homebrew/share/tessdata");
        } else if (osName.contains("linux")) {
            tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata");
        }
        
        return tesseract.doOCR(imagePath.toFile());
    }

    /**
     * Checks if OCR is available and properly configured.
     */
    public boolean isOcrAvailable() {
        if (!ocrEnabled) {
            return false;
        }
        
        try {
            // Try a simple OCR test
            BufferedImage testImage = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            String result = tesseract.doOCR(testImage);
            return true;
        } catch (Exception e) {
            log.warn("OCR is not available: {}", e.getMessage());
            return false;
        }
    }
}