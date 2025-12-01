package com.modlix.saas.files.service;

import java.io.IOException;
import java.util.Base64;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.commons2.exception.GenericException;

/**
 * Service for file conversion operations like converting binary files to base64.
 * Useful for handling iOS signing files (.p12 certificates, .mobileprovision profiles)
 * that need to be stored as base64 encoded strings.
 */
@Service
public class FileConversionService {

    /**
     * Converts a multipart file to a base64 encoded string.
     * 
     * @param file the multipart file to convert
     * @return base64 encoded string of the file content
     */
    public String convertToBase64(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "File is required and cannot be empty");
        }

        try {
            byte[] fileBytes = file.getBytes();
            return Base64.getEncoder().encodeToString(fileBytes);
        } catch (IOException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Converts a multipart file to base64 and returns detailed information.
     * 
     * @param file the multipart file to convert
     * @return FileBase64Result containing the base64 string and file metadata
     */
    public FileBase64Result convertToBase64WithDetails(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new GenericException(HttpStatus.BAD_REQUEST, "File is required and cannot be empty");
        }

        try {
            byte[] fileBytes = file.getBytes();
            String base64Content = Base64.getEncoder().encodeToString(fileBytes);
            
            return new FileBase64Result(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    file.getSize(),
                    base64Content
            );
        } catch (IOException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, 
                    "Failed to read file: " + e.getMessage());
        }
    }

    /**
     * Result object containing base64 encoded file content and metadata.
     */
    public record FileBase64Result(
            String fileName,
            String contentType,
            long size,
            String base64Content
    ) {}
}

