package com.hrflow.web;

import com.hrflow.dto.ImportDtos.ImportErrorDto;
import com.hrflow.dto.ImportDtos.ImportResultDto;
import com.hrflow.service.ImportService;
import com.hrflow.web.error.ApiExceptions.BusinessRuleException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** CSV bulk employee import (admin only; enforced in the service). */
@RestController
@RequestMapping("/api/admin")
public class EmployeeImportController {

    private final ImportService importService;

    public EmployeeImportController(ImportService importService) {
        this.importService = importService;
    }

    /** Upload a CSV; returns the batch summary and any row-level errors. */
    @PostMapping(value = "/employees/import", consumes = "multipart/form-data")
    public ImportResultDto importEmployees(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("Uploaded file is empty");
        }
        String filename = file.getOriginalFilename() == null ? "upload.csv" : file.getOriginalFilename();
        try (var reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8)) {
            return importService.importCsv(filename, reader);
        } catch (IOException e) {
            throw new BusinessRuleException("Could not read upload: " + e.getMessage());
        }
    }

    /** The errors recorded for a past import batch. */
    @GetMapping("/imports/{batchId}/errors")
    public List<ImportErrorDto> errors(@PathVariable Long batchId) {
        return importService.getErrors(batchId);
    }
}
