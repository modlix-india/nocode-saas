package com.fincity.saas.message.model.message.whatsapp.messages;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class MessageDeserializationTest {

    private static final String JSON_MESSAGE =
            """
        {
          "template": {
            "components": [
              {
                "type": "header",
                "parameters": [
                  {
                    "type": "document",
                    "document": {
                      "link": "https://dev.leadzump.ai/api/files/static/file/KANDU3/whatsapp_templates/sample+doc.pdf"
                    }
                  }
                ]
              },
              {
                "type": "body",
                "parameters": [
                  {
                    "type": "text",
                    "text": "Mr. Rajesh"
                  },
                  {
                    "type": "text",
                    "text": "Next Wave"
                  },
                  {
                    "type": "text",
                    "text": "Executive Full Stack Development Program"
                  },
                  {
                    "type": "text",
                    "text": "20"
                  },
                  {
                    "type": "text",
                    "text": "October 30, 2025"
                  }
                ]
              }
            ],
            "name": "course_enrollment_offer_document",
            "language": {
              "code": "en"
            }
          },
          "to": "+917995115729",
          "type": "template"
        }
        """;

    @Test
    void testDeserializeMessage() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        System.out.println("=== Attempting to deserialize JSON ===");
        System.out.println("JSON Input:");
        System.out.println(JSON_MESSAGE);
        System.out.println("\n=== Deserializing... ===");

        try {
            Message message = objectMapper.readValue(JSON_MESSAGE, Message.class);

            System.out.println("\n=== Deserialization SUCCESSFUL ===");
            System.out.println("Deserialized Message:");
            System.out.println("  - To: " + message.getTo());
            System.out.println("  - Type: " + message.getType());
            System.out.println("  - Messaging Product: " + message.getMessagingProduct());
            System.out.println("  - Recipient Type: " + message.getRecipientType());

            if (message.getTemplateMessage() != null) {
                System.out.println(
                        "\n  - Template Name: " + message.getTemplateMessage().getName());
                if (message.getTemplateMessage().getLanguage() != null) {
                    System.out.println("  - Language Code: "
                            + message.getTemplateMessage().getLanguage().getCode());
                }
                if (message.getTemplateMessage().getComponents() != null) {
                    System.out.println("  - Components Count: "
                            + message.getTemplateMessage().getComponents().size());
                    message.getTemplateMessage().getComponents().forEach(component -> {
                        System.out.println("    * Component Type: " + component.getType());
                        if (component.getParameters() != null) {
                            System.out.println("      Parameters Count: "
                                    + component.getParameters().size());
                        }
                    });
                }
            }

            System.out.println("\n=== Serializing back to JSON ===");
            String serialized = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(message);
            System.out.println(serialized);

            // Verify deserialization
            assertNotNull(message);
            assertEquals("+917995115729", message.getTo());
            assertNotNull(message.getType());
            assertNotNull(message.getTemplateMessage());
            assertEquals(
                    "course_enrollment_offer_document",
                    message.getTemplateMessage().getName());

        } catch (Exception e) {
            System.out.println("\n=== Deserialization FAILED ===");
            System.out.println("Error Type: " + e.getClass().getName());
            System.out.println("Error Message: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
