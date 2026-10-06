package com.financetracker.ingestion.controller;

import com.financetracker.ingestion.dto.StatementUploadResponse;
import com.financetracker.ingestion.entity.BankStatement;
import com.financetracker.ingestion.entity.StatementStatus;
import com.financetracker.ingestion.service.StatementIngestionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatementUploadControllerTest {

    @Mock
    private StatementIngestionService ingestionService;

    @InjectMocks
    private StatementUploadController controller;

    @Test
    void uploadStatement_withValidFile_returnsCreated() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "date,description,amount\n2026-01-01,Coffee,-4.50".getBytes());
        String bankName = "Test Bank";
        Long userId = 1L;

        BankStatement mockStatement = new BankStatement();
        mockStatement.setId(1L);
        mockStatement.setBankName(bankName);
        mockStatement.setOriginalFilename("statement.csv");
        mockStatement.setStatus(StatementStatus.PUBLISHED);
        mockStatement.setChunkCount(1);
        mockStatement.setCreatedAt(Instant.now());

        when(ingestionService.ingest(any(MultipartFile.class), eq(bankName), eq(userId)))
                .thenReturn(mockStatement);

        // Act
        ResponseEntity<StatementUploadResponse> response = controller.uploadStatement(file, bankName, userId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().id()).isEqualTo(1L);
        assertThat(response.getBody().bankName()).isEqualTo(bankName);
        assertThat(response.getBody().originalFilename()).isEqualTo("statement.csv");
    }

    @Test
    void uploadStatement_withoutUserId_defaultsToAdmin() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "date,description,amount\n2026-01-01,Coffee,-4.50".getBytes());
        String bankName = "Test Bank";

        BankStatement mockStatement = new BankStatement();
        mockStatement.setId(1L);
        mockStatement.setBankName(bankName);
        mockStatement.setOriginalFilename("statement.csv");
        mockStatement.setStatus(StatementStatus.PUBLISHED);
        mockStatement.setChunkCount(1);
        mockStatement.setCreatedAt(Instant.now());

        when(ingestionService.ingest(any(MultipartFile.class), eq(bankName), eq(1L)))
                .thenReturn(mockStatement);

        // Act
        ResponseEntity<StatementUploadResponse> response = controller.uploadStatement(file, bankName, null);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void uploadStatement_withCustomUserId_usesProvidedUserId() {
        // Arrange
        MultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv", "date,description,amount\n2026-01-01,Coffee,-4.50".getBytes());
        String bankName = "Test Bank";
        Long customUserId = 42L;

        BankStatement mockStatement = new BankStatement();
        mockStatement.setId(1L);
        mockStatement.setBankName(bankName);
        mockStatement.setOriginalFilename("statement.csv");
        mockStatement.setStatus(StatementStatus.PUBLISHED);
        mockStatement.setChunkCount(1);
        mockStatement.setCreatedAt(Instant.now());

        when(ingestionService.ingest(any(MultipartFile.class), eq(bankName), eq(customUserId)))
                .thenReturn(mockStatement);

        // Act
        ResponseEntity<StatementUploadResponse> response = controller.uploadStatement(file, bankName, customUserId);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
    }
}
