package com.fincity.saas.message.service.bridge;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappPhoneNumberDAO;
import com.fincity.saas.message.feign.IFeignFileService;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import java.nio.ByteBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Stores an attachment the bridge fetched out of WhatsApp.
 *
 * <p>The bridge does not talk to the files service and should not start. It runs in a different
 * trust zone with one outbound dependency - this service - and no credentials for anything else.
 * Handing it a files URL and a token to go with it would widen what an intrusion on a bridge host
 * reaches, for the sake of saving one hop that costs nothing.
 *
 * <p>So the bytes come here over the channel that is already authenticated, and this service, which
 * already knows which tenant a session belongs to, does the upload. That last part is the real
 * reason: the bridge has a session id and nothing else. It does not know the client code, and the
 * storage path is keyed on it.
 */
@Service
public class BridgeMediaService {

    private static final Logger logger = LoggerFactory.getLogger(BridgeMediaService.class);

    /**
     * How long a conversation attachment is kept: thirty days, in minutes.
     *
     * <p>Carried on the file itself rather than enforced by a sweep that works out which files are
     * old enough. That distinction is what keeps this safe. An age-based sweep over the media rows
     * would have deleted product brochures shared across many messages, because a lead was sent one
     * a month ago; a file that was never given a lifetime cannot be removed at all.
     *
     * <p>A constant for now. The per-client override goes where this is read, not where it is
     * stored, so raising it later does not touch anything already written.
     */
    private static final int RETENTION_MINUTES = 30 * 24 * 60;

    /** Secured, never static. An attachment is a customer's conversation, not a public asset. */
    private static final String RESOURCE_TYPE = "secured";

    private final WhatsappPhoneNumberDAO sessionDao;
    private final IFeignFileService fileService;

    public BridgeMediaService(WhatsappPhoneNumberDAO sessionDao, IFeignFileService fileService) {
        this.sessionDao = sessionDao;
        this.fileService = fileService;
    }

    /**
     * Uploads one attachment and answers where it landed.
     *
     * @return the files service's own {@code FileDetail}, passed back to the bridge verbatim so it
     *     can be attached to the MEDIA_READY event without this service having to model it.
     */
    public Mono<FileDetail> store(
            String sessionId,
            String messageId,
            String customerWaId,
            String mimeType,
            String fileName,
            boolean outbound,
            ByteBuffer body) {

        if (StringUtil.safeIsBlank(sessionId) || StringUtil.safeIsBlank(messageId))
            return Mono.error(new IllegalArgumentException("sessionId and messageId are both required"));

        return FlatMapUtil.flatMapMono(
                        () -> this.sessionDao.getBySessionIdInternal(sessionId),
                        session -> this.fileService.create(
                                RESOURCE_TYPE,
                                session.getClientCode(),
                                Boolean.FALSE,
                                directoryFor(session.getAppCode(), customerWaId, outbound),
                                storedNameFor(messageId, mimeType, fileName),
                                RETENTION_MINUTES,
                                body))
                .switchIfEmpty(Mono.defer(() -> {
                    // Same reasoning as the event path: retrying will not conjure the session row,
                    // so this fails the one attachment rather than stalling the queue behind it.
                    logger.error("Bridge sent media for unknown session {} (message {}). Dropping it.",
                            sessionId, messageId);
                    return Mono.empty();
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeMediaService.store"));
    }

    /**
     * Stores a customer's profile picture.
     *
     * <p>Kept out of the conversation's media tree on purpose, under {@code /whatsapp/{app}/avatars}
     * rather than under {@code incoming}. Attachment retention sweeps that tree, and an avatar must
     * not be swept: a photo expiring out of a thread is honest history, whereas a deal quietly
     * losing its face after thirty days is a bug that would look like a rendering fault.
     *
     * <p>Named after the customer's number rather than the event, and overwritten in place, so one
     * file serves every deal that number stands behind and changing a picture does not accumulate
     * copies of every face a person has ever had.
     */
    public Mono<FileDetail> storeAvatar(String sessionId, String customerWaId, ByteBuffer body) {

        if (StringUtil.safeIsBlank(sessionId) || StringUtil.safeIsBlank(customerWaId))
            return Mono.error(new IllegalArgumentException("sessionId and customerWaId are both required"));

        return FlatMapUtil.flatMapMono(
                        () -> this.sessionDao.getBySessionIdInternal(sessionId),
                        session -> this.fileService.create(
                                RESOURCE_TYPE,
                                session.getClientCode(),
                                // Overwrite. The alternative is a new file per change plus something
                                // to delete the old ones, for no benefit: nobody wants a customer's
                                // previous profile pictures.
                                Boolean.TRUE,
                                "/whatsapp/" + sanitise(session.getAppCode()) + "/avatars",
                                sanitise(customerWaId) + ".jpg",
                                // No lifetime. An avatar is not conversation history and must not
                                // vanish on the media schedule: an expired photo in a thread reads
                                // as expired, a deal losing its face reads as broken.
                                null,
                                body))
                .switchIfEmpty(Mono.defer(() -> {
                    logger.error("Bridge sent an avatar for unknown session {}. Dropping it.", sessionId);
                    return Mono.empty();
                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeMediaService.storeAvatar"));
    }

    /**
     * Reads back an attachment the agent is sending out.
     *
     * <p>No token, and that is deliberate. A token would be a second credential layered on a caller
     * that already proved itself: this route is HMAC-signed like every other bridge call, so only a
     * holder of the shared secret reaches it. What a token would genuinely add is one-shot semantics,
     * and the thing it would protect against - a bridge replaying its own fetch - is not a threat,
     * because a bridge that wanted the bytes twice can simply ask twice.
     *
     * <p>What does need checking is that the path belongs to the session's tenant. Without it, a
     * compromised bridge could name any path under any client and this service would fetch it. The
     * session decides the client, and the path has to sit under that client's WhatsApp tree.
     */
    public Mono<ByteBuffer> fetch(String sessionId, String filePath) {

        if (StringUtil.safeIsBlank(sessionId) || StringUtil.safeIsBlank(filePath))
            return Mono.error(new IllegalArgumentException("sessionId and filePath are both required"));

        return FlatMapUtil.flatMapMono(
                        () -> this.sessionDao.getBySessionIdInternal(sessionId),
                        session -> {
                            if (!ownedBy(filePath, session.getClientCode(), session.getAppCode())) {
                                logger.error(
                                        "Bridge asked for {} on session {}, which is outside client {}'s WhatsApp"
                                                + " files. Refusing.",
                                        filePath,
                                        sessionId,
                                        session.getClientCode());
                                return Mono.empty();
                            }
                            // Client-qualified on the way back in. create() answers a filePath
                            // relative to the client root, while the download resolves the client
                            // from the path itself - so handing back exactly what was stored asks
                            // for a file that, as far as the files service is concerned, is in
                            // nobody's directory.
                            return this.fileService.downloadFile(
                                    RESOURCE_TYPE, qualify(session.getClientCode(), filePath));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "BridgeMediaService.fetch"));
    }

    /**
     * Whether a path is one this session is allowed to read.
     *
     * <p>Rejects any traversal outright rather than normalising it. Normalising invites an arms race
     * over encodings, and nothing legitimate produces a ".." here: every path this service hands out
     * was built by storedNameFor from a sanitised message id.
     */
    private static boolean ownedBy(String filePath, String clientCode, String appCode) {
        if (filePath.contains("..")) return false;
        // Compared against the unqualified form, because that is the shape create() hands back and
        // therefore the shape the caller has. The client is enforced separately, by qualifying with
        // the session's own client rather than trusting one in the path.
        String normalised = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        return normalised.startsWith("whatsapp/" + sanitise(appCode) + "/");
    }

    /** Prefixes the client root the download resolves against. */
    private static String qualify(String clientCode, String filePath) {
        String normalised = filePath.startsWith("/") ? filePath.substring(1) : filePath;
        return clientCode + "/" + normalised;
    }

    /**
     * The folder an attachment lives in.
     *
     * <p>App code is in the path even though the files service scopes by client alone. Without it
     * two apps under one client share a whatsapp/ tree, and a per-app retention sweep or an audit
     * has no way to tell them apart afterwards.
     */
    private static String directoryFor(String appCode, String customerWaId, boolean outbound) {
        // Unknown rather than the message id when the bridge could not resolve the customer. Filing
        // it under its own id would scatter one conversation across a folder per message.
        String who = StringUtil.safeIsBlank(customerWaId) ? "unknown" : sanitise(customerWaId);
        return "/whatsapp/" + sanitise(appCode) + "/" + (outbound ? "outgoing" : "incoming") + "/" + who;
    }

    /**
     * The stored filename.
     *
     * <p>WhatsApp's message id, not the sender's filename. Two documents called Brochure.pdf in one
     * customer's folder would collide, and the message id is already the idempotency key for the
     * whole chain. The real name survives on the message row and is what the person downloading
     * sees, via the files service's own name parameter.
     */
    private static String storedNameFor(String messageId, String mimeType, String fileName) {
        return sanitise(messageId) + extensionFor(mimeType, fileName);
    }

    private static String extensionFor(String mimeType, String fileName) {

        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot > 0 && dot < fileName.length() - 1) return "." + sanitise(fileName.substring(dot + 1));

        if (StringUtil.safeIsBlank(mimeType)) return ".bin";

        int slash = mimeType.indexOf('/');
        if (slash < 0 || slash == mimeType.length() - 1) return ".bin";

        // "image/jpeg; codecs=..." and "audio/ogg; codecs=opus" both arrive from real handsets.
        String sub = mimeType.substring(slash + 1);
        int semi = sub.indexOf(';');
        if (semi >= 0) sub = sub.substring(0, semi);

        sub = sanitise(sub.trim());
        return sub.isEmpty() ? ".bin" : "." + sub;
    }

    /** Anything that is not plainly safe in a path segment becomes an underscore. */
    private static String sanitise(String raw) {
        return raw == null ? "" : raw.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
