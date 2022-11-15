package com.fincity.saas.files.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.files.enumerations.FilesSort;
import com.fincity.saas.files.model.FileDetail;

import reactor.core.publisher.Mono;

public abstract class AbstractFilesResourceService {

	@Autowired
	private FilesMessageResourceService msgService;

	public Mono<List<FileDetail>> list(String clientCode, String resourcePath, String filter, FilesSort sort) {

		Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

		return FlatMapUtil.flatMapMono(

		        () -> this.checkReadPermission(),

		        hasPermission -> hasPermission ? Mono.empty() : M

		);

		if (!Files.isDirectory(path))
			throw new PrimeException(HttpStatus.BAD_REQUEST, ASSET_NOT_A_DIRECTORY.getResponseMsg(),
			        ASSET_NOT_A_DIRECTORY.getResponseCode());

		if (filter == null || filter.trim()
		        .isEmpty())
			filter = "";
		else
			filter = filter.trim()
			        .toUpperCase();

		final String fileFilter = filter;

		if (sort == null)
			sort = FileSort.TYPE_ASC;

		try (Stream<Path> stream = Files.find(path, 1, (paths, attr) -> attr.isRegularFile() || attr.isDirectory())) {
			return stream.filter(e -> !e.equals(path))
			        .map(Path::toFile)
			        .filter(obj -> obj.getName()
			                .toUpperCase()
			                .contains(fileFilter))
			        .sorted(sort.getComparator())
			        .map(this::convertToSAMFile)
			        .collect(Collectors.toList());
		} catch (IOException ex) {
			logger.error("Unable to search folder {}.", path, ex);
			throw new PrimeException(HttpStatus.INTERNAL_SERVER_ERROR, ASSET_FILE_SEARCH_FAILURE.getResponseMsg(),
			        ASSET_FILE_SEARCH_FAILURE.getResponseCode());
		}
	}

	private Mono<Boolean> checkReadPermission() {
		return SecurityContextUtil
		        .hasAuthority("Authorities." + this.getResourceType() + "_Files_READ || Authorities."
		                + this.getResourceType() + "_Files_WRITE")
		        .flatMap(hasPermission -> hasPermission ? Mono.just(true)
		                : msgService.throwMessage(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_RESOURCE,
		                        "Read", this.getResourceType()));
	}

	public abstract String getBaseLocation();

	public abstract String getResourceType();
}
