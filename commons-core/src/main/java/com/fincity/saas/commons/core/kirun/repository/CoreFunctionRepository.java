package com.fincity.saas.commons.core.kirun.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.fincity.saas.commons.core.functions.ai.Chat;
import com.fincity.saas.commons.core.functions.crypto.SignatureValidator;
import com.fincity.saas.commons.core.functions.crypto.Signer;
import com.fincity.saas.commons.core.functions.email.SendEmail;
import com.fincity.saas.commons.core.functions.events.CreateEventFunction;
import com.fincity.saas.commons.core.functions.file.FileToBase64;
import com.fincity.saas.commons.core.functions.file.TemplateToPdf;
import com.fincity.saas.commons.core.functions.hash.HashData;
import com.fincity.saas.commons.core.functions.notification.SendNotification;
import com.fincity.saas.commons.core.functions.rest.CallRequest;
import com.fincity.saas.commons.core.functions.security.*;
import com.fincity.saas.commons.core.functions.securitycontext.GetAuthentication;
import com.fincity.saas.commons.core.functions.securitycontext.GetUser;
import com.fincity.saas.commons.core.functions.securitycontext.HasAuthority;
import com.fincity.saas.commons.core.functions.storage.CreateManyStorageObject;
import com.fincity.saas.commons.core.functions.storage.CreateStorageObject;
import com.fincity.saas.commons.core.functions.storage.DeleteStorageObject;
import com.fincity.saas.commons.core.functions.storage.DeleteStorageObjectWithFilter;
import com.fincity.saas.commons.core.functions.storage.ReadPageStorageObject;
import com.fincity.saas.commons.core.functions.storage.ReadStorageObject;
import com.fincity.saas.commons.core.functions.storage.UpdateStorageObject;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.NotificationService;
import com.fincity.saas.commons.core.service.connection.ai.AIService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.email.EmailService;
import com.fincity.saas.commons.core.service.connection.rest.RestService;
import com.fincity.saas.commons.core.service.file.TemplateConversionService;
import com.fincity.saas.commons.core.service.security.ClientUrlService;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.google.gson.Gson;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CoreFunctionRepository implements ReactiveRepository<ReactiveFunction> {

    private final Map<String, ReactiveFunction> repoMap = new HashMap<>();

    private final List<String> filterableNames;

    public CoreFunctionRepository(CoreFunctionRepositoryBuilder builder) {
        this.makeStorageFunctions(builder.appDataService, builder.objectMapper, builder.gson);
        this.makeRESTFunctions(builder.restService, builder.filesService, builder.securityService, builder.gson);
        this.makeSecurityContextFunctions(builder.userContextService, builder.clientUrlService, builder.gson);
        this.makeSecurityFunctions(builder.securityService, builder.gson);
        this.makeEmailFunctions(builder.emailService);
        this.makeCreateEventFunction(builder.ecService, builder.edService);
        this.makeFileFunctions(
                builder.filesService, builder.templateConversionService, builder.filesService, builder.gson);
        this.makeCryptoFunctions();
        this.makeHashingFunctions();
        this.makeNotificationFunctions(builder.notificationService);
        this.makeAIFunctions(builder.aiService);

        this.filterableNames = repoMap.values().stream()
                .map(ReactiveFunction::getSignature)
                .map(FunctionSignature::getFullName)
                .toList();
    }

    private void makeSecurityFunctions(IFeignSecurityService securityService, Gson gson) {
        ReactiveFunction isBeingManagedByCode = new IsBeingManagedByCode(securityService);
        ReactiveFunction isBeingManagedById = new IsBeingManagedById(securityService);
        ReactiveFunction isUserBeingManaged = new IsUserBeingManaged(securityService);
        ReactiveFunction getClient = new GetClient(securityService, gson);
        ReactiveFunction getUserAdminEmails = new GetUserAdminEmails(securityService, gson);

        this.addToRepoMap(isBeingManagedByCode, isBeingManagedById, isUserBeingManaged, getClient, getUserAdminEmails);
    }

    private void makeSecurityContextFunctions(
            ContextService userContextService, ClientUrlService clientUrlService, Gson gson) {
        ReactiveFunction hasAuthority = new HasAuthority(userContextService);
        ReactiveFunction getAuthentication = new GetAuthentication(userContextService, gson);
        ReactiveFunction getUser = new GetUser(userContextService, gson);
        ReactiveFunction getAppUrl = new GetAppUrl(clientUrlService);

        this.addToRepoMap(hasAuthority, getAuthentication, getUser, getAppUrl);
    }

    private void makeStorageFunctions(AppDataService appDataService, ObjectMapper objectMapper, Gson gson) {
        ReactiveFunction createStorage = new CreateStorageObject(appDataService, gson);
        ReactiveFunction createManyStorage = new CreateManyStorageObject(appDataService, gson);
        ReactiveFunction deleteStorage = new DeleteStorageObject(appDataService);
        ReactiveFunction updateStorage = new UpdateStorageObject(appDataService, gson);
        ReactiveFunction readStorage = new ReadStorageObject(appDataService, gson);
        ReactiveFunction readPageStorage = new ReadPageStorageObject(appDataService, objectMapper, gson);
        ReactiveFunction deleteByFilterStorage = new DeleteStorageObjectWithFilter(appDataService, objectMapper, gson);

        this.addToRepoMap(
                createStorage,
                createManyStorage,
                deleteStorage,
                updateStorage,
                readStorage,
                readPageStorage,
                deleteByFilterStorage);
    }

    private void makeRESTFunctions(
            RestService restService,
            IFeignFilesService filesService,
            IFeignSecurityService securityService,
            Gson gson) {
        ReactiveFunction getRequest =
                new CallRequest(restService, filesService, securityService, "GetRequest", "GET", false, gson);
        ReactiveFunction postRequest =
                new CallRequest(restService, filesService, securityService, "PostRequest", "POST", true, gson);
        ReactiveFunction putRequest =
                new CallRequest(restService, filesService, securityService, "PutRequest", "PUT", true, gson);
        ReactiveFunction patchRequest =
                new CallRequest(restService, filesService, securityService, "PatchRequest", "PATCH", true, gson);
        ReactiveFunction deleteRequest =
                new CallRequest(restService, filesService, securityService, "DeleteRequest", "DELETE", false, gson);
        ReactiveFunction callRequest =
                new CallRequest(restService, filesService, securityService, "CallRequest", "", true, gson);

        this.addToRepoMap(getRequest, postRequest, putRequest, patchRequest, deleteRequest, callRequest);
    }

    private void makeEmailFunctions(EmailService emailService) {
        ReactiveFunction sendEmail = new SendEmail(emailService);
        this.addToRepoMap(sendEmail);
    }

    private void makeCreateEventFunction(EventCreationService ecService, EventDefinitionService edService) {
        ReactiveFunction createEvent = new CreateEventFunction(ecService, edService);
        this.addToRepoMap(createEvent);
    }

    private void makeNotificationFunctions(NotificationService notificationService) {
        ReactiveFunction sendNotification = new SendNotification(notificationService);
        this.addToRepoMap(sendNotification);
    }

    private void makeFileFunctions(
            IFeignFilesService filesService,
            TemplateConversionService templateConversionService,
            IFeignFilesService fileService,
            Gson gson) {
        ReactiveFunction fileToBase64 = new FileToBase64(filesService);
        ReactiveFunction templateToPdf = new TemplateToPdf(templateConversionService, fileService, gson);
        this.addToRepoMap(fileToBase64, templateToPdf);
    }

    private void makeCryptoFunctions() {
        ReactiveFunction signer = new Signer();
        ReactiveFunction signatureValidator = new SignatureValidator();

        this.addToRepoMap(signer, signatureValidator);
    }

    private void makeHashingFunctions() {
        ReactiveFunction hashData = new HashData();

        this.addToRepoMap(hashData);
    }

    private void makeAIFunctions(AIService aiService) {
        ReactiveFunction chatFunction = new Chat(aiService);
        this.addToRepoMap(chatFunction);
    }

    private void addToRepoMap(ReactiveFunction... functions) {
        Arrays.stream(functions)
                .forEach(function -> repoMap.putIfAbsent(function.getSignature().getFullName(), function));
    }

    @Override
    public Flux<String> filter(String name) {
        final String filterName = name == null ? "" : name;
        return Flux.fromStream(filterableNames.stream())
                .filter(e -> e.toLowerCase().contains(filterName.toLowerCase()));
    }

    @Override
    public Mono<ReactiveFunction> find(String namespace, String name) {
        return Mono.justOrEmpty(repoMap.get(namespace + "." + name));
    }

    @Data
    @Accessors(chain = true)
    public static class CoreFunctionRepositoryBuilder {

        private AppDataService appDataService;
        private ObjectMapper objectMapper;
        private RestService restService;
        private ContextService userContextService;
        private IFeignSecurityService securityService;
        private ClientUrlService clientUrlService;
        private EmailService emailService;
        private IFeignFilesService filesService;
        private TemplateConversionService templateConversionService;
        private Gson gson;
        private NotificationService notificationService;
        private AIService aiService;
        private EventCreationService ecService;
        private EventDefinitionService edService;
    }
}
