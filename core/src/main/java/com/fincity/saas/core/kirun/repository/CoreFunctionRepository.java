package com.fincity.saas.core.kirun.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.model.FunctionSignature;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.core.functions.crypto.SignatureValidator;
import com.fincity.saas.commons.core.functions.crypto.Signer;
import com.fincity.saas.commons.core.functions.email.SendEmail;
import com.fincity.saas.commons.core.functions.file.FileToBase64;
import com.fincity.saas.commons.core.functions.file.TemplateToPdf;
import com.fincity.saas.commons.core.functions.hash.HashData;
import com.fincity.saas.commons.core.functions.rest.CallRequest;
import com.fincity.saas.commons.core.functions.security.GetAppUrl;
import com.fincity.saas.commons.core.functions.security.GetClient;
import com.fincity.saas.commons.core.functions.security.IsBeingManagedByCode;
import com.fincity.saas.commons.core.functions.security.IsBeingManagedById;
import com.fincity.saas.commons.core.functions.security.IsUserBeingManaged;
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
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.email.EmailService;
import com.fincity.saas.commons.core.service.file.TemplateConversionService;
import com.fincity.saas.commons.core.service.security.ClientUrlService;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.core.feign.IFeignFilesService;
import com.fincity.saas.commons.core.service.connection.rest.RestService;
import com.google.gson.Gson;
import lombok.Data;
import lombok.experimental.Accessors;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoreFunctionRepository implements ReactiveRepository<ReactiveFunction> {

    private final Map<String, ReactiveFunction> repoMap = new HashMap<>();

    private final List<String> filterableNames;

    public CoreFunctionRepository(CoreFunctionRepositoryBuilder builder) {
        this.makeStorageFunctions(builder.appDataService, builder.objectMapper, builder.gson);
        this.makeRESTFunctions(builder.restService, builder.filesService, builder.securityService, builder.gson);
        this.makeSecurityContextFunctions(builder.userContextService, builder.clientUrlService, builder.gson);
        this.makeSecurityFunctions(builder.securityService, builder.gson);
        this.makeEmailFunctions(builder.emailService);
        this.makeFileFunctions(
                builder.filesService, builder.templateConversionService, builder.filesService, builder.gson);
        this.makeCryptoFunctions();
        this.makeHashingFunctions();

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

        repoMap.put(isBeingManagedByCode.getSignature().getFullName(), isBeingManagedByCode);
        repoMap.put(isBeingManagedById.getSignature().getFullName(), isBeingManagedById);
        repoMap.put(isUserBeingManaged.getSignature().getFullName(), isUserBeingManaged);
        repoMap.put(getClient.getSignature().getFullName(), getClient);
    }

    private void makeSecurityContextFunctions(
            ContextService userContextService, ClientUrlService clientUrlService, Gson gson) {

        ReactiveFunction hasAuthority = new HasAuthority(userContextService);
        ReactiveFunction getAuthentication = new GetAuthentication(userContextService, gson);
        ReactiveFunction getUser = new GetUser(userContextService, gson);
        ReactiveFunction getAppUrl = new GetAppUrl(clientUrlService);

        repoMap.put(hasAuthority.getSignature().getFullName(), hasAuthority);
        repoMap.put(getAuthentication.getSignature().getFullName(), getAuthentication);
        repoMap.put(getUser.getSignature().getFullName(), getUser);
        repoMap.put(getAppUrl.getSignature().getFullName(), getAppUrl);
    }

    private void makeStorageFunctions(AppDataService appDataService, ObjectMapper objectMapper, Gson gson) {

        ReactiveFunction createStorage = new CreateStorageObject(appDataService, gson);
        ReactiveFunction createManyStorage = new CreateManyStorageObject(appDataService, gson);
        ReactiveFunction deleteStorage = new DeleteStorageObject(appDataService);
        ReactiveFunction updateStorage = new UpdateStorageObject(appDataService, gson);
        ReactiveFunction readStorage = new ReadStorageObject(appDataService, gson);
        ReactiveFunction readPageStorage = new ReadPageStorageObject(appDataService, objectMapper, gson);
        ReactiveFunction deleteByFilterStorage = new DeleteStorageObjectWithFilter(appDataService, objectMapper, gson);

        repoMap.put(createStorage.getSignature().getFullName(), createStorage);
        repoMap.put(createManyStorage.getSignature().getFullName(), createManyStorage);
        repoMap.put(deleteStorage.getSignature().getFullName(), deleteStorage);
        repoMap.put(deleteByFilterStorage.getSignature().getFullName(), deleteByFilterStorage);
        repoMap.put(updateStorage.getSignature().getFullName(), updateStorage);
        repoMap.put(readStorage.getSignature().getFullName(), readStorage);
        repoMap.put(readPageStorage.getSignature().getFullName(), readPageStorage);
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

        repoMap.put(getRequest.getSignature().getFullName(), getRequest);
        repoMap.put(postRequest.getSignature().getFullName(), postRequest);
        repoMap.put(putRequest.getSignature().getFullName(), putRequest);
        repoMap.put(patchRequest.getSignature().getFullName(), patchRequest);
        repoMap.put(deleteRequest.getSignature().getFullName(), deleteRequest);
        repoMap.put(callRequest.getSignature().getFullName(), callRequest);
    }

    private void makeEmailFunctions(EmailService emailService) {

        ReactiveFunction sendEmail = new SendEmail(emailService);

        repoMap.put(sendEmail.getSignature().getFullName(), sendEmail);
    }

    private void makeFileFunctions(
            IFeignFilesService filesService,
            TemplateConversionService templateConversionService,
            IFeignFilesService fileService,
            Gson gson) {

        ReactiveFunction fileToBase64 = new FileToBase64(filesService);
        ReactiveFunction templateToPdf = new TemplateToPdf(templateConversionService, fileService, gson);

        repoMap.put(fileToBase64.getSignature().getFullName(), fileToBase64);
        repoMap.put(templateToPdf.getSignature().getFullName(), templateToPdf);
    }

    private void makeCryptoFunctions() {

        ReactiveFunction signer = new Signer();
        ReactiveFunction signatureValidator = new SignatureValidator();

        repoMap.put(signer.getSignature().getFullName(), signer);
        repoMap.put(signatureValidator.getSignature().getFullName(), signatureValidator);
    }

    private void makeHashingFunctions() {

        ReactiveFunction hashData = new HashData();

        repoMap.put(hashData.getSignature().getFullName(), hashData);
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
    }
}
