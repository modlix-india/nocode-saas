package com.fincity.saas.commons.core.service.connection.email;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.document.Template;
import com.fincity.saas.commons.core.model.ProcessedEmailDetails;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Sends through a minimal SMTP server on localhost, so the exceptions are the ones jakarta.mail really throws for a
 * refused recipient. The first reply below is what OCI Email Delivery sends for an address on its suppression list.
 */
class SMTPServiceTest {

    private static final String SUPPRESSED = "254 4.7.1 -  gone@example.com is suppressed for sender ocid1.emailsender";

    private StubSmtp server;

    @AfterEach
    void stop() throws Exception {
        if (server != null) server.close();
    }

    @Test
    void suppressedRecipientIsNotSentInsteadOfAnError() throws Exception {
        server = new StubSmtp(Map.of("gone@example.com", SUPPRESSED));

        StepVerifier.create(send(List.of("gone@example.com"), Map.of()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void permanentlyRejectedRecipientIsNotSent() throws Exception {
        server = new StubSmtp(Map.of("gone@example.com", "550 5.1.1 no such user"));

        StepVerifier.create(send(List.of("gone@example.com"), Map.of()))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void partialSendCountsAsSent() throws Exception {
        server = new StubSmtp(Map.of("gone@example.com", "550 5.1.1 no such user"));

        StepVerifier.create(send(List.of("ok@example.com", "gone@example.com"), Map.of("mail.smtp.sendpartial", "true")))
                .expectNext(true)
                .verifyComplete();
        assertEquals(1, server.messagesAccepted());
    }

    @Test
    void acceptedRecipientIsSent() throws Exception {
        server = new StubSmtp(Map.of());

        StepVerifier.create(send(List.of("ok@example.com"), Map.of()))
                .expectNext(true)
                .verifyComplete();
        assertEquals(1, server.messagesAccepted());
    }

    private Mono<Boolean> send(List<String> to, Map<String, String> extraProps) {
        Map<String, Object> mailProps = new HashMap<>(extraProps);
        mailProps.put("mail.smtp.host", "127.0.0.1");
        mailProps.put("mail.smtp.port", String.valueOf(server.port()));

        Connection connection = new Connection();
        connection.setConnectionDetails(
                Map.of("mailProps", mailProps, "username", "user", "password", "secret"));

        SMTPService service = new SMTPService() {
            @Override
            protected Mono<ProcessedEmailDetails> getProcessedEmailDetails(
                    Connection conn, List<String> toAddresses, Template template, Map<String, Object> data) {
                return Mono.just(new ProcessedEmailDetails()
                        .setTo(toAddresses)
                        .setFrom("noreply@example.com")
                        .setSubject("Welcome")
                        .setBody("<p>Hi</p>"));
            }
        };

        return service.sendMail(to, new Template(), Map.of(), connection);
    }

    /** One connection at a time; RCPT replies come from the map, everything else is accepted. */
    private static final class StubSmtp implements AutoCloseable {

        private final ServerSocket socket = new ServerSocket(0);
        private final Map<String, String> rcptReplies;
        private final List<String> accepted = new ArrayList<>();
        private final Thread thread;

        StubSmtp(Map<String, String> rcptReplies) throws Exception {
            this.rcptReplies = rcptReplies;
            this.thread = new Thread(this::serve, "stub-smtp");
            this.thread.setDaemon(true);
            this.thread.start();
        }

        int port() {
            return socket.getLocalPort();
        }

        synchronized int messagesAccepted() {
            return accepted.size();
        }

        private void serve() {
            while (!socket.isClosed()) {
                try (Socket s = socket.accept();
                        BufferedReader in =
                                new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.US_ASCII));
                        PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.US_ASCII)) {
                    reply(out, "220 stub ready");
                    String line;
                    while ((line = in.readLine()) != null) {
                        String cmd = line.toUpperCase();
                        if (cmd.startsWith("EHLO") || cmd.startsWith("HELO")) reply(out, "250 stub");
                        else if (cmd.startsWith("RCPT")) {
                            String addr = line.substring(line.indexOf('<') + 1, line.indexOf('>'));
                            reply(out, rcptReplies.getOrDefault(addr, "250 ok"));
                        } else if (cmd.startsWith("DATA")) {
                            reply(out, "354 go ahead");
                            while ((line = in.readLine()) != null && !line.equals("."))
                                ;
                            synchronized (this) {
                                accepted.add("message");
                            }
                            reply(out, "250 queued");
                        } else if (cmd.startsWith("QUIT")) {
                            reply(out, "221 bye");
                            break;
                        } else reply(out, "250 ok");
                    }
                } catch (Exception e) {
                    if (socket.isClosed()) return;
                }
            }
        }

        private static void reply(PrintWriter out, String line) {
            out.print(line + "\r\n");
            out.flush();
        }

        @Override
        public void close() throws Exception {
            socket.close();
            thread.join(2000);
        }
    }
}
