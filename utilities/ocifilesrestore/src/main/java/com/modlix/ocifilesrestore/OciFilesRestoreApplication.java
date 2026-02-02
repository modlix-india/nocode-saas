package com.modlix.ocifilesrestore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.oracle.bmc.ConfigFileReader;
import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.model.ObjectVersionSummary;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectVersionsRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectVersionsResponse;

@SpringBootApplication
public class OciFilesRestoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(OciFilesRestoreApplication.class, args);
    }

    @Bean
    public CommandLineRunner runOciOperations() {
        return args -> {
            try {
                System.out.println("Starting OCI Files Restore Application...");

                // Read OCI configuration
                ConfigFileReader.ConfigFile config = ConfigFileReader.parse("~/.oci/config", "DEFAULT");
                System.out.println("OCI configuration loaded successfully");

                // Create authentication provider
                AuthenticationDetailsProvider provider = new ConfigFileAuthenticationDetailsProvider(config);
                System.out.println("OCI authentication provider created");

                // Create Object Storage client
                ObjectStorageClient objectStorageClient = ObjectStorageClient.builder()
                        .build(provider);
                System.out.println("OCI Object Storage client created");

                // Get namespace and bucket names from config or use defaults
                String namespaceName = "idfmutpuhiky";
                String bucketName = "dev-static";

                System.out.println("Using namespace: " + namespaceName);
                System.out.println("Using bucket: " + bucketName);

                // List deleted versions of objects
                System.out.println("Searching for deleted objects starting with 'SYSTEM/'...");

                ListObjectVersionsRequest listVersionsRequest = ListObjectVersionsRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .prefix("SYSTEM/")
                        .build();

                //Create a fixed thread pool of 50 threads
                ExecutorService executorService = Executors.newFixedThreadPool(50);
                List<Future<Void>> futures = new ArrayList<>();

                do {
                    ListObjectVersionsResponse versionsResponse = objectStorageClient
                            .listObjectVersions(listVersionsRequest);

                    System.out.println("\nSearching for deleted versions...");
                    int deletedCount = 0;
                    int totalVersions = versionsResponse.getObjectVersionCollection().getItems().size();
                    System.out.println("Total versions found: " + totalVersions);

                     for (ObjectVersionSummary version : versionsResponse.getObjectVersionCollection().getItems()) {
                         if (!version.getIsDeleteMarker())
                             continue;

                         deletedCount++;
                         LocalDateTime modifiedTime = version.getTimeModified().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                        if (modifiedTime.isBefore(LocalDateTime.now().minusDays(1))){
                            System.out.println("  Skipping "+ version.getName() +" version modified before yesterday: " + modifiedTime);
                            continue;
                        }
                         
                         // Find the previous version (actual file content) before deletion
                         String objectName = version.getName();
                         
                         // Submit download task to thread pool
                         Future<Void> future = executorService.submit(() -> {
                             try {
                                 System.out.println("  [Thread] Processing: " + objectName);
                                 
                                 // Get all versions of this specific object
                                 ListObjectVersionsRequest specificObjectRequest = ListObjectVersionsRequest.builder()
                                         .namespaceName(namespaceName)
                                         .bucketName(bucketName)
                                         .prefix(objectName)
                                         .build();
                                 
                                 ListObjectVersionsResponse specificVersions = objectStorageClient.listObjectVersions(specificObjectRequest);
                                 
                                 // Find the last non-delete-marker version (the actual file content)
                                 ObjectVersionSummary lastGoodVersion = null;
                                 for (ObjectVersionSummary v : specificVersions.getObjectVersionCollection().getItems()) {
                                     if (v.getName().equals(objectName) && !v.getIsDeleteMarker()) {
                                         lastGoodVersion = v;
                                         break; // Get the most recent non-delete-marker version
                                     }
                                 }
                                 
                                 if (lastGoodVersion != null) {
                                     System.out.println("  [Thread] Found previous version: " + lastGoodVersion.getVersionId() + " for " + objectName);
                                     
                                     // Download the actual file content (not the delete marker)
                                     GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                             .namespaceName(namespaceName)
                                             .bucketName(bucketName)
                                             .objectName(objectName)
                                             .versionId(lastGoodVersion.getVersionId())
                                             .build();
                                     
                                     GetObjectResponse getObjectResponse = objectStorageClient.getObject(getObjectRequest);
                                     Path filePath = Paths.get("./deletedFiles/" + objectName);
                                     Files.createDirectories(filePath.getParent());
                                     Files.write(filePath, getObjectResponse.getInputStream().readAllBytes());
                                     System.out.println("  [Thread] ✓ Downloaded: " + objectName);
                                 } else {
                                     System.out.println("  [Thread] ✗ No previous version found for: " + objectName);
                                 }
                             } catch (Exception e) {
                                 System.err.println("  [Thread] ✗ Error processing " + objectName + ": " + e.getMessage());
                             }
                             return null;
                         });
                         
                         futures.add(future);
                     }

                    if (deletedCount == 0) {
                        break;
                    } else {
                        System.out.println("Found " + deletedCount + " deleted object versions.");
                    }
                    if (versionsResponse.getOpcNextPage() == null)
                        break;
                    versionsResponse = objectStorageClient.listObjectVersions(listVersionsRequest);
                    listVersionsRequest = ListObjectVersionsRequest.builder()
                            .namespaceName(namespaceName)
                            .bucketName(bucketName)
                            .prefix("SYSTEM/")
                            .page(versionsResponse.getOpcNextPage())
                            .build();
                 } while (true);

                 // Wait for all download tasks to complete
                 System.out.println("\nWaiting for all download tasks to complete...");
                 for (Future<Void> future : futures) {
                     try {
                         future.get(); // Wait for each task to complete
                     } catch (Exception e) {
                         System.err.println("Error waiting for task completion: " + e.getMessage());
                     }
                 }
                 
                 // Shutdown the executor service
                 executorService.shutdown();
                 System.out.println("All download tasks completed!");

                 objectStorageClient.close();
                 System.out.println("OCI Files Restore operation completed successfully!");

            } catch (IOException e) {
                System.err.println("Error reading OCI config file: " + e.getMessage());
                System.err.println("Please ensure ~/.oci/config exists and is properly formatted.");
            } catch (Exception e) {
                System.err.println("Error during OCI operation: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}