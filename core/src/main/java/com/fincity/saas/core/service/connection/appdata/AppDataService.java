package com.fincity.saas.core.service.connection.appdata;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.IntStream;

import javax.annotation.PostConstruct;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.SchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.util.FlatFileType;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.document.Storage;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.model.DataObject;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.fincity.saas.core.service.CoreSchemaService;
import com.fincity.saas.core.service.StorageService;
import com.monitorjbl.xlsx.StreamingReader;
import com.opencsv.CSVWriter;
import com.opencsv.ICSVWriter;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class AppDataService {

	private static final ConnectionSubType DEFAULT_APP_DATA_SERVICE = ConnectionSubType.MONGO;

	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private StorageService storageService;

	@Autowired
	private MongoAppDataService mongoAppDataService;

	@Autowired
	private CoreSchemaService schemaService;

	@Autowired
	private CoreMessageResourceService msgService;

	private EnumMap<ConnectionSubType, IAppDataService> services = new EnumMap<>(ConnectionSubType.class);

	@PostConstruct
	public void init() {

		this.services.putAll(Map.of(ConnectionSubType.MONGO, (IAppDataService) mongoAppDataService));
	}

	public Mono<Map<String, Object>> create(String appCode, String clientCode, String storageName,
	        DataObject dataObject) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.create(conn, storage, dataObject), Storage::getCreateAuth,
		                CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE));
	}

	public Mono<Map<String, Object>> update(String appCode, String clientCode, String storageName,
	        DataObject dataObject, Boolean override) {

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.update(conn, storage, dataObject, override),
		                Storage::getUpdateAuth, CoreMessageResourceService.FORBIDDEN_UPDATE_STORAGE));
	}

	public Mono<Map<String, Object>> read(String appCode, String clientCode, String storageName, String id) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.read(conn, storage, id), Storage::getReadAuth,
		                CoreMessageResourceService.FORBIDDEN_READ_STORAGE));
	}

	public Mono<Page<Map<String, Object>>> readPage(String appCode, String clientCode, String storageName,
	        Pageable page, Boolean count, AbstractCondition condition) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.readPage(conn, storage, page, count, condition),
		                Storage::getReadAuth, CoreMessageResourceService.FORBIDDEN_READ_STORAGE));
	}

	public Mono<Boolean> delete(String appCode, String clientCode, String storageName, String id) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this.genericOperation(storage,
		                (ca, hasAccess) -> dataService.delete(conn, storage, id), Storage::getDeleteAuth,
		                CoreMessageResourceService.FORBIDDEN_DELETE_STORAGE));
	}

	public Mono<byte[]> downloadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType,
	        ServerHttpRequest request, ServerHttpResponse response) {
		return FlatMapUtil.flatMapMonoWithNull(

		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> this
		                .genericOperation(storage, (ca, hasAccess) -> downloadTemplate(storage, fileType),
		                        Storage::getCreateAuth, CoreMessageResourceService.FORBIDDEN_CREATE_STORAGE)
		                .switchIfEmpty(Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
		                        CoreMessageResourceService.NOT_ABLE_TO_OPEN_FILE_ERROR))));
	}

	public Mono<Boolean> uploadTemplate(String appCode, String clientCode, String storageName, FlatFileType fileType,
	        Flux<MultipartFile> filePart, ServerHttpRequest request, ServerHttpResponse response) {

		return FlatMapUtil.flatMapMonoWithNull(
		        () -> connectionService.find(appCode, clientCode, ConnectionType.APP_DATA),

		        conn -> Mono
		                .just(this.services.get(conn == null ? DEFAULT_APP_DATA_SERVICE : conn.getConnectionSubType())),

		        (conn, dataService) -> storageService.read(storageName, appCode, clientCode),

		        (conn, dataService, storage) -> Mono.from(filePart.collectList()),

		        (conn, dataService, storage, file) -> this.uploadTemplate(conn, storage, fileType, file, request,
		                response));

	}

	// upload template method schema implement here .

	private <T> Mono<T> genericOperation(Storage storage, BiFunction<ContextAuthentication, Boolean, Mono<T>> bifun,
	        Function<Storage, String> authFun, String msgString) {

		if (storage == null)
			return msgService.throwMessage(HttpStatus.NOT_FOUND, CoreMessageResourceService.STORAGE_NOT_FOUND);

		return FlatMapUtil.flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.justOrEmpty(SecurityContextUtil.hasAuthority(authFun.apply(storage), ca.getUser()
		                .getAuthorities()) ? true : null),

		        bifun)

		        .switchIfEmpty(Mono
		                .defer(() -> this.msgService.throwMessage(HttpStatus.FORBIDDEN, msgString, storage.getName())));
	}

	private Mono<byte[]> downloadTemplate(Storage storage, FlatFileType type) {

		try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();) {

			return FlatMapUtil.flatMapMono(() -> storageService.getSchema(storage),

			        storageSchema ->
					{

				        List<String> headers = this.getHeaders(null, storage, storageSchema);

				        if (type == FlatFileType.XLSX) {
					        try (XSSFWorkbook excelWorkbook = new XSSFWorkbook();) {

						        XSSFSheet sheet = excelWorkbook.createSheet(storage.getName());
						        Row headRow = sheet.createRow(0); // writing for header
						        int headColumn = 0;
						        for (String header : headers) {
							        Cell cell = headRow.createCell(headColumn++);
							        cell.setCellValue(header);
						        }
						        excelWorkbook.write(byteStream);
						        return Mono.just(byteStream.toByteArray());

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }

				        } else if (type == FlatFileType.CSV) {

					        try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
					                CSVWriter csvWorkBook = new CSVWriter(outputStream);) {

						        csvWorkBook.writeNext(headers.toArray(new String[0]));
						        outputStream.flush();
						        return Mono.just(byteStream.toByteArray());

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }

				        } else if (type == FlatFileType.TSV) {

					        try (OutputStreamWriter outputStream = new OutputStreamWriter(byteStream);
					                CSVWriter tsvWorkBook = new CSVWriter(outputStream, '\t',
					                        ICSVWriter.DEFAULT_QUOTE_CHARACTER, ICSVWriter.DEFAULT_ESCAPE_CHARACTER,
					                        ICSVWriter.DEFAULT_LINE_END);) {

						        tsvWorkBook.writeNext(headers.toArray(new String[0]));
						        outputStream.flush();
						        return Mono.just(byteStream.toByteArray());

					        } catch (Exception e) {
						        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
						                CoreMessageResourceService.TEMPLATE_GENERATION_ERROR, type.toString()));
					        }
				        }
				        return Mono.empty();
			        });

		} catch (Exception e) {
			return Mono.empty();
		}

	}

	private List<String> getHeaders(String prefix, Storage storage, Schema schema) {

		if (schema.getRef() != null) {

			schema = SchemaUtil.getSchemaFromRef(schema,
			        this.schemaService.getSchemaRepository(storage.getAppCode(), storage.getClientCode()),
			        schema.getRef());
		}

		if (schema.getType()
		        .contains(SchemaType.OBJECT)) {

			return schema.getProperties()
			        .entrySet()
			        .stream()
			        .flatMap(e -> this
			                .getHeaders(prefix == null ? e.getKey() : prefix + "." + e.getKey(), storage, e.getValue())
			                .stream())
			        .toList();
		} else if (schema.getType()
		        .contains(SchemaType.ARRAY)) {

			ArraySchemaType aType = schema.getItems();

			if (aType.getSingleSchema() != null) {

				return IntStream.range(0, 2)
				        .mapToObj(e -> prefix == null ? "[" + e + "]" : prefix + "[" + e + "]")
				        .flatMap(e -> this.getHeaders(e, storage, aType.getSingleSchema())
				                .stream())
				        .toList();
			} else {

				List<String> list = new ArrayList<>();

				for (int i = 0; i < aType.getTupleSchema()
				        .size(); i++) {

					String pre = prefix == null ? "[" + i + "]" : prefix + "[" + i + "]";

					list.addAll(this.getHeaders(pre, storage, aType.getTupleSchema()
					        .get(i)));
				}

				return list;
			}
		}

		return List.of(prefix);
	}

	public Mono<Boolean> uploadTemplate(Connection conn, Storage storage, FlatFileType fileType,
	        List<MultipartFile> filePart, ServerHttpRequest request, ServerHttpResponse response) {

		return this.uploadTemplateWithoutAuth(conn, storage, fileType, filePart, request, response);
	}

	public Mono<Boolean> uploadTemplateWithoutAuth(Connection conn, Storage storage, FlatFileType fileType,
	        List<MultipartFile> filePart, ServerHttpRequest request, ServerHttpResponse response) {

		return FlatMapUtil.flatMapMono(

		        () -> storageService.getSchema(storage),

		        storageSchema ->
				{
			        System.out.println(storageSchema);

			        System.out.println(filePart);

			        // check the type and obtain headers check it is present in same format as such
			        // in uploaded file

			        for (MultipartFile file : filePart) {

				        try (InputStream is = file.getInputStream();
				                Workbook excelWorkbook = StreamingReader.builder()
				                        .rowCacheSize(100)
				                        .bufferSize(4096)
				                        .open(is);) {
					        List<String> headers = this.getHeaders(null, storage, storageSchema);

					        // extract first row from excel as headers
					        // header validation

					        Row headerRow = excelWorkbook.getSheetAt(0)
					                .getRow(0);

					        for (Cell c : headerRow) {
						        if (StringUtil.safeEquals(c.getStringCellValue(), headers.get(c.getColumnIndex()))) {
							        return Mono.defer(() -> this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
							                CoreMessageResourceService.HEADERS_NOT_CORRECT_ORDER));
						        }
					        }

					        // if headers are correct then do the unflat and obtain the schema
					        // object from list of headers which are given with values against them

					        for (Sheet sheet : excelWorkbook) {
						        for (Row r : sheet) {
							        for (Cell c : r) {
								        Object cellValue = c.getStringCellValue();
								        // create storage data object from here and verify it is matching with schema
								        // validator. If matches then bulk upload otherwise ignore error rows and
								        // continue with other rows
//							        if(cellValue instanceof String)
								        // use for string purpose

							        }
						        }
					        }

				        } catch (Exception ex) {
					        ex.printStackTrace();
				        }
			        }
			        return Mono.just(true);
		        }

		// pass the list
		);

	}

}
