package com.modlix.saas.files.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import javax.imageio.ImageIO;

import org.jooq.types.ULong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.dto.Client;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.model.User;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.commons2.util.HashUtil;
import com.modlix.saas.files.dao.FileSystemDao;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.model.ImageDetails;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Client logos and user photos are stored by id under SYSTEM/_clientImages and SYSTEM/_userImages.
 * These pin down which id names the file, who may write someone else's photo, and that replacing a
 * file at the same path drops the locally cached download.
 */
@ExtendWith(MockitoExtension.class)
class ImageUploadNamingAndAccessTest {

    private static final BigInteger SELF_ID = BigInteger.valueOf(142);
    private static final BigInteger SELF_CLIENT_ID = BigInteger.valueOf(77);
    private static final BigInteger OTHER_USER_ID = BigInteger.valueOf(500);
    private static final BigInteger OTHER_CLIENT_ID = BigInteger.valueOf(88);
    private static final String APP_CODE = "sitezump";

    @Mock
    private IFeignSecurityService securityService;
    @Mock
    private FilesMessageResourceService msgService;
    @Mock
    private FileSystemService fsService;

    private MockedStatic<SecurityContextUtil> securityContext;

    @BeforeEach
    void setUp() {
        securityContext = Mockito.mockStatic(SecurityContextUtil.class, invocation -> {
            if ("hasAuthority".equals(invocation.getMethod().getName()) && invocation.getArguments().length == 2)
                return invocation.callRealMethod();
            return Mockito.RETURNS_DEFAULTS.answer(invocation);
        });

        lenient().when(msgService.throwMessage(any(), eq(FilesMessageResourceService.FORBIDDEN_PATH), any(), any()))
                .thenAnswer(invocation -> {
                    Function<String, GenericException> fn = invocation.getArgument(0);
                    throw fn.apply("forbidden");
                });

        lenient().when(fsService.createFileFromFile(eq("SYSTEM"), anyString(), anyString(), any(Path.class),
                anyBoolean()))
                .thenAnswer(invocation -> new FileDetail().setName(invocation.getArgument(2)));
    }

    @AfterEach
    void tearDown() {
        securityContext.close();
    }

    private void signIn(String... authorities) {
        ContextUser user = new ContextUser();
        user.setId(SELF_ID);
        user.setClientId(SELF_CLIENT_ID);
        user.setStringAuthorities(List.of(authorities));

        ContextAuthentication ca = new ContextAuthentication();
        ca.setUser(user);
        ca.setAuthenticated(true);
        ca.setClientCode("SELFCL");
        ca.setUrlAppCode(APP_CODE);

        securityContext.when(SecurityContextUtil::getUsersContextAuthentication).thenReturn(ca);
    }

    private static MockMultipartFile png() throws IOException {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return new MockMultipartFile("file", "photo.png", "image/png", out.toByteArray());
    }

    private static ImageDetails details() {
        ImageDetails details = new ImageDetails();
        details.setWidth(4);
        details.setHeight(4);
        return details;
    }

    private static void setField(Object target, Class<?> declaring, String name, Object value) {
        try {
            Field field = declaring.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private StaticFileResourceService staticService() {
        StaticFileResourceService service = new StaticFileResourceService(null, msgService, null, null, null, null,
                null, securityService);
        setField(service, StaticFileResourceService.class, "fileSystemService", fsService);
        return service;
    }

    private SecuredFileResourceService securedService() {
        SecuredFileResourceService service = new SecuredFileResourceService(null, null, msgService, null, null,
                null, null, null, securityService, msgService);
        setField(service, SecuredFileResourceService.class, "fileSystemService", fsService);
        return service;
    }

    // ---------------------------------------------------------------- client logo

    @Test
    void ownLogoWithoutClientIdIsNamedAfterTheCallersClient() throws IOException {

        signIn("Authorities.Client_UPDATE");

        FileDetail fd = staticService().uploadClientImage(png(), details(), null);

        verify(fsService).createFileFromFile(eq("SYSTEM"), eq("_clientImages"), eq("77.png"), any(Path.class),
                eq(true));
        assertEquals("77.png", fd.getName());
        verify(fsService, never()).createFileFromFile(anyString(), anyString(), eq("null.png"), any(Path.class),
                anyBoolean());
        verifyNoInteractions(securityService);
    }

    @Test
    void managedClientsLogoIsNamedAfterThatClient() throws IOException {

        signIn("Authorities.Client_UPDATE");
        when(securityService.isUserClientManageClient(APP_CODE, SELF_ID, SELF_CLIENT_ID, OTHER_CLIENT_ID))
                .thenReturn(Boolean.TRUE);
        Client other = new Client();
        other.setCode("OTHER");
        when(securityService.getClientById(OTHER_CLIENT_ID)).thenReturn(other);

        staticService().uploadClientImage(png(), details(), ULong.valueOf(OTHER_CLIENT_ID));

        verify(fsService).createFileFromFile(eq("SYSTEM"), eq("_clientImages"), eq("88.png"), any(Path.class),
                eq(true));
    }

    // ---------------------------------------------------------------- user photo

    @Test
    void ownPhotoWithoutUserIdNeedsNoCheck() throws IOException {

        signIn("Authorities.Logged_IN");

        FileDetail fd = securedService().uploadUserImage(png(), details(), null);

        assertEquals("142.png", fd.getName());
        verifyNoInteractions(securityService);
    }

    @Test
    void ownPhotoWithOwnUserIdNeedsNoCheck() throws IOException {

        signIn("Authorities.Logged_IN");

        securedService().uploadUserImage(png(), details(), ULong.valueOf(SELF_ID));

        verify(fsService).createFileFromFile(eq("SYSTEM"), eq("_userImages"), eq("142.png"), any(Path.class),
                eq(true));
        verifyNoInteractions(securityService);
    }

    @Test
    void someoneElsesPhotoAsksWhetherTheCallerManagesTheTargetsClient() throws IOException {

        signIn("Authorities.User_UPDATE");
        User target = new User();
        target.setId(OTHER_USER_ID);
        target.setClientId(OTHER_CLIENT_ID);
        when(securityService.getUserInternal(eq(OTHER_USER_ID), isNull())).thenReturn(target);
        when(securityService.isUserClientManageClient(APP_CODE, SELF_ID, SELF_CLIENT_ID, OTHER_CLIENT_ID))
                .thenReturn(Boolean.TRUE);

        securedService().uploadUserImage(png(), details(), ULong.valueOf(OTHER_USER_ID));

        // (appCode, CALLER id, CALLER client, TARGET client) - not the other way round.
        verify(securityService).isUserClientManageClient(APP_CODE, SELF_ID, SELF_CLIENT_ID, OTHER_CLIENT_ID);
        verify(fsService).createFileFromFile(eq("SYSTEM"), eq("_userImages"), eq("500.png"), any(Path.class),
                eq(true));
    }

    @Test
    void someoneElsesPhotoIsRefusedWhenTheCallerDoesNotManageThem() throws IOException {

        signIn("Authorities.User_UPDATE");
        User target = new User();
        target.setId(OTHER_USER_ID);
        target.setClientId(OTHER_CLIENT_ID);
        when(securityService.getUserInternal(eq(OTHER_USER_ID), isNull())).thenReturn(target);
        when(securityService.isUserClientManageClient(APP_CODE, SELF_ID, SELF_CLIENT_ID, OTHER_CLIENT_ID))
                .thenReturn(Boolean.FALSE);

        SecuredFileResourceService service = securedService();
        MockMultipartFile file = png();
        ImageDetails details = details();

        GenericException ex = assertThrows(GenericException.class,
                () -> service.uploadUserImage(file, details, ULong.valueOf(OTHER_USER_ID)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fsService, never()).createFileFromFile(anyString(), anyString(), anyString(), any(Path.class),
                anyBoolean());
    }

    @Test
    void someoneElsesPhotoIsRefusedWithoutUserUpdate() throws IOException {

        signIn("Authorities.Logged_IN");
        User target = new User();
        target.setId(OTHER_USER_ID);
        target.setClientId(SELF_CLIENT_ID);
        when(securityService.getUserInternal(eq(OTHER_USER_ID), isNull())).thenReturn(target);

        SecuredFileResourceService service = securedService();
        MockMultipartFile file = png();
        ImageDetails details = details();

        GenericException ex = assertThrows(GenericException.class,
                () -> service.uploadUserImage(file, details, ULong.valueOf(OTHER_USER_ID)));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(fsService, never()).createFileFromFile(anyString(), anyString(), anyString(), any(Path.class),
                anyBoolean());
    }

    // ---------------------------------------------------------------- download cache

    @Test
    void overwritingAFileDropsTheCachedDownloadCopy() throws IOException {

        FileSystemDao dao = mock(FileSystemDao.class);
        CacheService cacheService = mock(CacheService.class);
        S3Client s3Client = mock(S3Client.class);

        FileSystemService fs = new FileSystemService(dao, cacheService, "unit-test-static", s3Client,
                mock(S3AsyncClient.class), FilesFileSystemType.STATIC, msgService);

        Path tempFolder;
        try {
            Field field = FileSystemService.class.getDeclaredField("tempFolder");
            field.setAccessible(true);
            tempFolder = (Path) field.get(fs);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }

        // Where getAsFile keeps the copy it downloaded of SYSTEM/_clientImages/77.png.
        Path cached = tempFolder.resolve(
                Path.of(HashUtil.sha256Hash(Path.of("SYSTEM/_clientImages")), "77.png"));
        Files.createDirectories(cached.getParent());
        Files.write(cached, new byte[] { 1, 2, 3 });

        File served = fs.getAsFile("SYSTEM/_clientImages/77.png", false);
        assertEquals(cached.toFile(), served, "a cached copy is served without going to the bucket");

        when(cacheService.cacheValueOrGet(anyString(), any(), any())).thenReturn(Boolean.TRUE);

        byte[] replacement = new byte[] { 9, 9, 9, 9, 9 };
        fs.createFileFromInputStream("SYSTEM", "_clientImages", "77.png", new ByteArrayInputStream(replacement),
                replacement.length, true, "inline");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        assertFalse(Files.exists(cached), "the overwrite must drop the stale cached copy");

        try (var walk = Files.walk(tempFolder)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
        assertTrue(Files.notExists(tempFolder));
    }
}
