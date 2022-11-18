package com.fincity.saas.files.service;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.util.FileExtensionUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractFilesResourceService {

	@Autowired
	private FilesMessageResourceService msgService;

	@Autowired
	private FileAccessPathService fileAccessService;

	private static Logger logger = LoggerFactory.getLogger(AbstractFilesResourceService.class);

	private static final Map<String, Comparator<File>> COMPARATORS = new HashMap<>(Map.of(

	        "TYPE",
	        Comparator.<File, String>comparing(e -> e.isDirectory() ? " " : FileExtensionUtil.get(e.getName()),
	                String.CASE_INSENSITIVE_ORDER),

	        "SIZE", Comparator.comparingLong(File::length),

	        "NAME", Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER),

	        "LASTMODIFIED", Comparator.comparingLong(File::lastModified)));

	public Mono<Page<FileDetail>> list(String clientCode, String resourcePath, String filter, Pageable page) {

		return FlatMapUtil.flatMapMono(

		        () -> this.fileAccessService.hasReadAccess(resourcePath, clientCode,
		                FilesAccessPathResourceType.STATIC),

		        hasPermission ->
				{

			        if (!hasPermission.booleanValue())
				        return msgService.throwMessage(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PATH,
				                this.getResourceType(), resourcePath);

			        String rp = URLDecoder.decode(resourcePath, StandardCharsets.UTF_8).replace('+',' ');

			        Path path = Paths.get(this.getBaseLocation(), clientCode, rp);

			        if (!Files.isDirectory(path))
				        return msgService.throwMessage(HttpStatus.BAD_REQUEST,
				                FilesMessageResourceService.NOT_A_DIRECTORY, rp);

			        String nameFilter = "";
			        if (filter == null || filter.trim()
			                .isEmpty())
				        nameFilter = "";
			        else
				        nameFilter = filter.trim()
				                .toUpperCase();

			        Comparator<File> sortComparator = getComparator(page);

			        try {

				        Stream<Path> stream = Files.find(path, 1,
				                (paths, attr) -> attr.isRegularFile() || attr.isDirectory());

				        String stringNameFilter = nameFilter;

				        return Flux.fromStream(stream)
				                .filter(e -> !e.equals(path))
				                .map(Path::toFile)
				                .filter(obj -> obj.getName()
				                        .toUpperCase()
				                        .contains(stringNameFilter))
				                .sort(sortComparator)
				                .map(e -> this.convertToFileDetail(resourcePath, clientCode, e))
				                .skip(page.getOffset())
				                .take(page.getPageSize())
				                .collectList();
			        } catch (IOException ex) {
				        return msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR, ex,
				                FilesMessageResourceService.UNKNOWN_ERROR);
			        }
		        })
		        .map(list -> PageableExecutionUtils.getPage(list, page, () -> -1));
	}

	private Comparator<File> getComparator(Pageable page) {

		if (page == null || page.getSort()
		        .isEmpty() || page.getSort()
		                .isUnsorted()) {

			return COMPARATORS.get("TYPE");
		} else {

			return page.getSort()
			        .stream()
			        .map(e ->
					{
				        if (!COMPARATORS.containsKey(e.getProperty()
				                .toUpperCase()))
					        return null;

				        if (e.getDirection()
				                .isDescending())
					        return COMPARATORS.get(e.getProperty())
					                .reversed();

				        return COMPARATORS.get(e.getProperty());
			        })
			        .filter(Objects::nonNull)
			        .reduce(Comparator::thenComparing)
			        .orElse(COMPARATORS.get("TYPE"));
		}
	}

	private FileDetail convertToFileDetail(String resourcePath, String clientCode, File file) {

		FileDetail damFile = new FileDetail().setName(file.getName())
		        .setFilePath(resourcePath + "/" + file.getName())
		        .setUrl("api/files/static" + (file.isDirectory() ? "" : "/file/" + clientCode) + resourcePath + "/"
		                + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8).replace("+", "%20"))
		        .setDirectory(file.isDirectory())
		        .setSize(file.length());
		try {
			BasicFileAttributes basicAttrributes = Files.readAttributes(file.toPath(), BasicFileAttributes.class);
			if (basicAttrributes != null) {
				damFile.setCreatedDate(basicAttrributes.creationTime()
				        .toMillis())
				        .setLastAccessTime(basicAttrributes.lastAccessTime()
				                .toMillis())
				        .setLastModifiedTime(basicAttrributes.lastModifiedTime()
				                .toMillis());
			}
		} catch (IOException e) {

			logger.debug("Unable to read attributes of file {} ", file.getAbsolutePath(), e);
		}
		return damFile;
	}
	
	public Mono<Void> downloadFile(DownloadOptions downloadOptions, ServerHttpRequest request, ServerHttpResponse response) {
		
		return FlatMapUtil.flatMapMono(
				
				() -> this.resolveFileToRead(request.getURI().toString()),
				
				fp -> {

					
					
					ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
					response.getHeaders()
					        .set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + path. + "");
					response.getHeaders()
					        .setContentType(MediaType.APPLICATION_OCTET_STREAM);
					
					return zeroCopyResponse.writeWith(file, 0, file.length());
				}
				);
	}

	public abstract String getBaseLocation();

	public abstract String getResourceType();

	public abstract Mono<Path> resolveFileToRead(String filePath);
}
