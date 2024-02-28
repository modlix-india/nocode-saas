package com.fincity.saas.files.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;

import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.codec.ResourceHttpMessageWriter;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.util.FileSystemUtils;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.FileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.util.FileExtensionUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class AbstractFilesResourceService {

	private static final String GENERIC_URI_PART = "api/files/";

	private static final String GENERIC_URI_PART_FILE = "/file";

	private static final String GENERIC_URI_PART_IMPORT = "/import";

	private static final String STATIC_TYPE = "static";

	private static final String SECURED_TYPE = "secured";
	
	private static final String TRANSFORM_TYPE = "transform";

	private static final String GENERIC_URI_PART_STATIC = GENERIC_URI_PART + STATIC_TYPE;

	private static final String GENERIC_URI_PART_SECURED = GENERIC_URI_PART + SECURED_TYPE;

	private static final String UNABLE_TO_READ_ATTRIBUTES = "Unable to read attributes of file {} ";

	private static Logger logger = LoggerFactory.getLogger(AbstractFilesResourceService.class);

	@Autowired
	private FilesMessageResourceService msgService;

	@Autowired
	protected FilesAccessPathService fileAccessService;

	private String uriPart;
	private String uriPartFile;
	private String uriPartImport;

	@PostConstruct
	private void initialize() {

		String type = this.getResourceType()
				.toString()
				.toLowerCase();

		this.uriPart = GENERIC_URI_PART + type;

		this.uriPartFile = GENERIC_URI_PART + type + GENERIC_URI_PART_FILE;

		this.uriPartImport = GENERIC_URI_PART + type + GENERIC_URI_PART_IMPORT;
	}

	private static final Map<String, Comparator<File>> COMPARATORS = new HashMap<>(Map.of(

			"TYPE",
			Comparator.<File, String>comparing(e -> e.isDirectory() ? " " : FileExtensionUtil.get(e.getName()),
					String.CASE_INSENSITIVE_ORDER),

			"SIZE", Comparator.comparingLong(File::length),

			"NAME", Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER),

			"LASTMODIFIED", Comparator.comparingLong(File::lastModified)));

	public Mono<Page<FileDetail>> list(String clientCode, String uri, FileType[] fileType, String filter,
			Pageable page) {

		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
		String resourcePath = tup.getT1();

		return FlatMapUtil.flatMapMono(

				() -> this.fileAccessService.hasReadAccess(resourcePath, clientCode, this.getResourceType()),

				hasPermission -> {

					if (!hasPermission.booleanValue())
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);

					Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

					if (!Files.exists(path))
						this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								FilesMessageResourceService.PATH_NOT_FOUND, resourcePath);

					if (!Files.isDirectory(path))
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								FilesMessageResourceService.NOT_A_DIRECTORY, resourcePath);

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

						String stringNameFilter = nameFilter.toUpperCase();

						return Flux.fromStream(stream)
								.filter(e -> !e.equals(path))
								.map(Path::toFile)
								.filter(obj -> obj.getName()
										.toUpperCase()
										.contains(stringNameFilter))
								.sort(sortComparator)
								.map(e -> this.convertToFileDetail(resourcePath, clientCode, e))
								.filter(getPredicateForFileTypes(fileType))
								.skip(page.getOffset())
								.take(page.getPageSize())
								.collectList();
					} catch (IOException ex) {
						return msgService.throwMessage(
								msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, ex),
								FilesMessageResourceService.UNKNOWN_ERROR);
					}
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.list"))
				.map(list -> PageableExecutionUtils.getPage(list, page, () -> -1));
	}

	private Predicate<FileDetail> getPredicateForFileTypes(FileType[] fileType) {

		final Set<String> fileTypeFilter = this.getFileExtensionFilter(fileType);
		final boolean directoryFilter = fileType != null && Stream.of(fileType)
				.anyMatch(e -> e == FileType.DIRECTORIES);

		Predicate<FileDetail> filterFunction = e -> true;

		if (fileTypeFilter.isEmpty() && directoryFilter)
			filterFunction = e -> e.isDirectory();
		else if (!fileTypeFilter.isEmpty() && !directoryFilter)
			filterFunction = e -> fileTypeFilter.contains(e.getType());
		else if (!fileTypeFilter.isEmpty() && directoryFilter)
			filterFunction = e -> fileTypeFilter.contains(e.getType()) || e.isDirectory();
		return filterFunction;
	}

	private Comparator<File> getComparator(Pageable page) {

		if (page == null || page.getSort()
				.isEmpty() || page.getSort()
						.isUnsorted()) {

			return COMPARATORS.get("TYPE");
		} else {

			return page.getSort()
					.stream()
					.map(e -> {
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

	private String getResourceFileType() {

		return this.getResourceType()
				.equals(FilesAccessPathResourceType.STATIC) ? GENERIC_URI_PART_STATIC : GENERIC_URI_PART_SECURED;

	}

	private Set<String> getFileExtensionFilter(FileType[] fileType) {

		if (fileType == null || fileType.length == 0)
			return Set.of();
		else
			return Stream.of(fileType)
					.map(FileType::getAvailableFileExtensions)
					.flatMap(Set::stream)
					.collect(Collectors.toSet());

	}

	private FileDetail convertToFileDetailWhileCreation(String resourcePath, String clientCode, File file) {

		FileDetail damFile = new FileDetail().setName(file.getName())
				.setSize(file.length());

		String resourceType = this.getResourceFileType();

		if (file.isDirectory()) {

			damFile.setFilePath(resourcePath)
					.setUrl(resourceType + resourcePath)
					.setDirectory(true);

		} else {
			damFile.setFilePath(resourcePath + "/" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
					.replace("+", "%20"))
					.setUrl(resourceType + ("/file/" + clientCode) + resourcePath + "/"
							+ URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
									.replace("+", "%20"));
		}
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

			logger.debug(UNABLE_TO_READ_ATTRIBUTES, file.getAbsolutePath(), e);
		}
		return damFile;

	}

	private FileDetail convertToFileDetail(String resourcePath, String clientCode, File file) {

		String resourceType = this.getResourceFileType();

		FileDetail damFile = new FileDetail().setName(file.getName())
				.setFilePath(resourcePath + "/" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
						.replace("+", "%20"))
				.setUrl(resourceType + (file.isDirectory() ? "" : "/file/" + clientCode) + resourcePath + "/"
						+ URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
								.replace("+", "%20"))
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

			logger.debug(UNABLE_TO_READ_ATTRIBUTES, file.getAbsolutePath(), e);
		}
		return damFile;
	}

	public Mono<Void> downloadFile(DownloadOptions downloadOptions, ServerHttpRequest request,
			ServerHttpResponse response) {

		return FlatMapUtil.flatMapMono(

				() -> Mono.just(this.resolvePathWithClientCode(request.getURI()
						.toString()))
						.map(Tuple2::getT1),

				this::checkReadAccessWithClientCode,

				(rp, hasAccess) -> {

					if (!hasAccess.booleanValue())
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), rp);

					Path file = Paths.get(this.getBaseLocation(), rp);

					if (!Files.exists(file))
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								FilesMessageResourceService.PATH_NOT_FOUND, rp);

					long fileMillis = -1;
					try {

						BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
						fileMillis = attr.lastModifiedTime()
								.toMillis();
					} catch (IOException e) {

						logger.debug(UNABLE_TO_READ_ATTRIBUTES, file, e);
					}

					String fileETag = new StringBuilder().append('"')
							.append(file.hashCode())
							.append('-')
							.append(fileMillis)
							.append('-')
							.append(downloadOptions.eTagCode())
							.append('"')
							.toString();

					if (Files.isDirectory(file))
						return downloadDirectory(downloadOptions, request, response, rp, file, fileMillis, fileETag);

					return makeMatchesStartDownload(downloadOptions, request, response, file, fileMillis, fileETag);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.downloadFile"));
	}

	private Mono<Void> downloadDirectory(DownloadOptions downloadOptions, ServerHttpRequest request,
			ServerHttpResponse response, String rp, Path file, long fileMillis, String fileETag) {
		int ind = rp.charAt(0) == '/' ? 1 : 0;
		int secondInd = rp.indexOf('/', ind);

		String sp = secondInd == -1 ? rp.substring(ind) : rp.substring(ind, secondInd);
		final long finFileMillis = fileMillis;

		return this.fileAccessService
				.hasReadAccess(secondInd == -1 && secondInd < rp.length() ? "" : rp.substring(secondInd + 1), sp,
						this.getResourceType())
				.flatMap(e -> {
					if (!e.booleanValue()) {
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), rp);
					}
					return makeMatchesStartDownload(downloadOptions, request, response, file, finFileMillis, fileETag);
				});
	}

	/**
	 * 
	 * @param resourcePath the path of the resource to which the access need to be
	 *                     checked.
	 * @return a Mono if the readAccess is granted by the requester
	 */
	protected Mono<Boolean> checkReadAccessWithClientCode(String resourcePath) {
		return Mono.just(true);
	}

	protected Mono<Void> makeMatchesStartDownload(DownloadOptions downloadOptions, ServerHttpRequest request,
			ServerHttpResponse response, Path file, long fileMillis, String fileETag) {
		var respHeaders = response.getHeaders();
		var reqHeaders = request.getHeaders();

		if (downloadOptions.getNoCache()
				.booleanValue())
			return sendFile(downloadOptions, fileETag, fileMillis, file, request, response);

		long modifiedSince = reqHeaders.getIfModifiedSince();
		if (fileMillis != -1 && modifiedSince != -1 && fileMillis == modifiedSince) {
			return sendHitResponse(respHeaders, response);
		}

		String eTag = reqHeaders.getETag();
		if (eTag == null) {
			List<String> matches = reqHeaders.getIfNoneMatch();
			if (!matches.isEmpty())
				eTag = matches.get(0);
		}

		if (fileETag.equals(eTag))
			return sendHitResponse(respHeaders, response);

		return sendFile(downloadOptions, fileETag, fileMillis, file, request, response);
	}

	private Mono<Void> sendHitResponse(HttpHeaders respHeaders, ServerHttpResponse response) {

		respHeaders.set("x-cache", "HIT");
		respHeaders.setContentLength(0l);
		response.setStatusCode(HttpStatus.NOT_MODIFIED);
		return response.setComplete();
	}

	public Mono<Void> sendFile(DownloadOptions downloadOptions, String eTag, long fileMillis, Path file,
			ServerHttpRequest request, ServerHttpResponse response) {

		HttpHeaders respHeaders = response.getHeaders();

		File actualFile = file.toFile();

		if (Files.isDirectory(file)) {
			try {
				downloadOptions.setDownload(true);
				downloadOptions.setName(file.getFileName()
						.toString() + ".zip");

				file = this.makeArchive(file);
				actualFile = file.toFile();
			} catch (IOException e) {
				return this.msgService.throwMessage(
						msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
						FilesMessageResourceService.UNABLE_CREATE_DOWNLOAD_FILE);
			}
		}

		respHeaders.set("x-cache", "MISS");
		respHeaders.setLastModified(fileMillis);
		respHeaders.setETag(eTag);
		if (!downloadOptions.getNoCache()
				.booleanValue())
			respHeaders.setCacheControl("public, max-age=3600");
		respHeaders.setContentDisposition((downloadOptions.getDownload()
				.booleanValue() ? ContentDisposition.attachment() : ContentDisposition.inline())
				.filename(downloadOptions.getName() == null ? file.getFileName()
						.toString() : downloadOptions.getName())
				.build());
		String mimeType = URLConnection.guessContentTypeFromName(file.getFileName()
				.toString());
		if (mimeType == null) {
			logger.debug("Unable to find mimetype of file {}", file.toAbsolutePath());
			mimeType = "application/octet-stream";
		}
		respHeaders.setContentType(MediaType.valueOf(mimeType));

		long length = actualFile.length();

		List<HttpRange> ranges = request.getHeaders()
				.getRange();

		if (ranges.isEmpty()) {

			return sendFileWhenNoRanges(downloadOptions, file, response, respHeaders, actualFile, length);
		} else {

			return sendFileWhenRanges(downloadOptions, request, response, actualFile);
		}
	}

	private Path makeArchive(Path file) throws IOException {

		Path tmpFile = Files.createTempFile("tmp", "zip");
		try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(tmpFile.toFile()))) {
			this.zipFile(file.toFile(), tmpFile.getFileName()
					.toString(), zipOut);
		}

		return tmpFile;
	}

	private void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		if (fileToZip.isHidden()) {
			return;
		}
		if (fileToZip.isDirectory()) {
			if (fileName.endsWith("/")) {
				zipOut.putNextEntry(new ZipEntry(fileName));
				zipOut.closeEntry();
			} else {
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				zipOut.closeEntry();
			}
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
			}
			return;
		}
		try (FileInputStream fis = new FileInputStream(fileToZip)) {
			ZipEntry zipEntry = new ZipEntry(fileName);
			zipOut.putNextEntry(zipEntry);
			byte[] bytes = new byte[1024];
			int length;
			while ((length = fis.read(bytes)) >= 0) {
				zipOut.write(bytes, 0, length);
			}
		}
	}

	private Mono<Void> sendFileWhenRanges(DownloadOptions downloadOptions, ServerHttpRequest request,
			ServerHttpResponse response, File actualFile) {

		ResourceHttpMessageWriter writer = new ResourceHttpMessageWriter();
		if (!downloadOptions.hasModification()) {
			return writer.write(Mono.just(new FileSystemResource(actualFile)), null,
					ResolvableType.forClass(File.class), null, request, response, Map.of());
		} else {
			byte[] bytes = this.applyOptionsMakeResource(downloadOptions, actualFile);

			if (bytes.length == 0)
				return writer.write(Mono.just(new FileSystemResource(actualFile)), null,
						ResolvableType.forClass(File.class), null, request, response, Map.of());

			return writer.write(Mono.just(new ByteArrayResource(bytes)), null, ResolvableType.forClass(File.class),
					null, request, response, Map.of());
		}
	}

	private Mono<Void> sendFileWhenNoRanges(DownloadOptions downloadOptions, Path file, ServerHttpResponse response,
			HttpHeaders respHeaders, File actualFile, long length) {

		ZeroCopyHttpOutputMessage zeroCopyResponse = (ZeroCopyHttpOutputMessage) response;
		if (!downloadOptions.hasModification()) {

			respHeaders.setRange(List.of(HttpRange.createByteRange(0, length - 1)));
			respHeaders.setContentLength(length);
			return zeroCopyResponse.writeWith(file, 0, length);
		} else {

			byte[] bytes = this.applyOptionsMakeResource(downloadOptions, actualFile);

			if (bytes.length == 0) {
				respHeaders.setRange(List.of(HttpRange.createByteRange(0, length - 1)));
				respHeaders.setContentLength(length);
				return zeroCopyResponse.writeWith(file, 0, length);
			}

			respHeaders.setRange(List.of(HttpRange.createByteRange(0, bytes.length - 1l)));
			respHeaders.setContentLength(bytes.length);

			return zeroCopyResponse.writeWith(Mono.just(response.bufferFactory()
					.wrap(bytes)));
		}
	}

	private String imageType(String fileName) {
		if (StringUtil.safeIsBlank(fileName))
			return null;
		int index = fileName.lastIndexOf('.');
		if (index == -1)
			return null;
		return fileName.substring(index + 1);
	}

	private byte[] applyOptionsMakeResource(DownloadOptions options, File file) {

		try {

			BufferedImage image = makeImage(options, file);
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			boolean didItWrite = ImageIO.write(image, imageType(file.getName()), baos);
			if (!didItWrite)
				throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to write the image.");
			image.flush();
			return baos.toByteArray();
		} catch (Exception ex) {
			logger.debug("Image resize issue", ex);
			return new byte[0];
		}
	}

	private BufferedImage makeImage(DownloadOptions options, File file) throws IOException {

		BufferedImage image = ImageIO.read(file);
		Scalr.Mode scalingMode = options.getKeepAspectRatio()
				.booleanValue() ? Scalr.Mode.FIT_TO_WIDTH : Scalr.Mode.FIT_EXACT;
		if (scalingMode != Scalr.Mode.FIT_EXACT
				&& options.getResizeDirection() == DownloadOptions.ResizeDirection.VERTICAL) {
			scalingMode = Scalr.Mode.FIT_TO_HEIGHT;
		}

		image = Scalr.resize(image, Scalr.Method.ULTRA_QUALITY, scalingMode,
				options.getWidth() == null ? image.getWidth() : options.getWidth(),
				options.getHeight() == null ? image.getHeight() : options.getHeight(), Scalr.OP_ANTIALIAS);

		if (!options.getKeepAspectRatio()
				.booleanValue() || options.getBandColor() == null)

			return image;

		return applyBands(options, image, scalingMode);
	}

	private BufferedImage applyBands(DownloadOptions options, BufferedImage image, Scalr.Mode scalingMode) {

		if ((scalingMode == Scalr.Mode.FIT_TO_WIDTH && options.getHeight() != null)
				|| (scalingMode == Scalr.Mode.FIT_TO_HEIGHT && options.getWidth() != null)) {

			BufferedImage bImage = new BufferedImage(
					scalingMode == Scalr.Mode.FIT_TO_HEIGHT ? options.getWidth() : image.getWidth(),
					scalingMode == Scalr.Mode.FIT_TO_WIDTH ? options.getHeight() : image.getHeight(),
					BufferedImage.TYPE_INT_RGB);

			Graphics2D g2d = bImage.createGraphics();

			g2d.setColor(Color.decode(options.getBandColor()
					.startsWith("#") ? options.getBandColor() : "#" + options.getBandColor()));
			g2d.fillRect(0, 0, bImage.getWidth(), bImage.getHeight());
			g2d.drawImage(image,
					scalingMode == Scalr.Mode.FIT_TO_WIDTH ? 0 : (bImage.getWidth() - image.getWidth()) / 2,
					scalingMode == Scalr.Mode.FIT_TO_HEIGHT ? 0 : (bImage.getHeight() - image.getHeight()) / 2, null);

			g2d.dispose();
			return bImage;
		}

		return image;
	}

	public Mono<Boolean> delete(String clientCode, String uri) {

		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
		String resourcePath = tup.getT1();

		return FlatMapUtil.flatMapMono(

				() -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),

				hasPermission -> {

					if (!hasPermission.booleanValue()) {
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);
					}

					Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

					if (Files.isDirectory(path)) {

						try {
							return Mono.just(FileSystemUtils.deleteRecursively(path));
						} catch (IOException e) {
							this.msgService.throwMessage(
									msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
									FilesMessageResourceService.UNABLE_TO_DEL_FILE, path.toString());
						}
					} else {
						try {
							return Mono.just(Files.deleteIfExists(path));
						} catch (IOException e) {
							this.msgService.throwMessage(
									msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
									FilesMessageResourceService.UNABLE_TO_DEL_FILE, path.toString());
						}
					}

					return Mono.just(true);
				}

		)
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.delete"));
	}

	public Mono<FileDetail> create(String clientCode, String uri, FilePart fp, String fileName, Boolean override) {

		boolean ovr = override == null || override.booleanValue();
		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
		String resourcePath = tup.getT1();
		String urlResourcePath = tup.getT2();

		return FlatMapUtil.flatMapMonoWithNull(

				() -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),

				hasPermission -> {

					if (!hasPermission.booleanValue())
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);

					Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

					return this.createOrGetPath(path, urlResourcePath, fp, fileName, ovr);
				},

				(hasPermission, file) -> {

					if (fp == null)
						return Mono.just(
								this.convertToFileDetailWhileCreation(urlResourcePath, clientCode, file.toFile()));
					
					return FlatMapUtil
							.flatMapMonoWithNull(() -> fp.transferTo(file),
									x -> Mono.just(this.convertToFileDetailWhileCreation(urlResourcePath, clientCode,
											file.toFile())))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
	}

	public Mono<FileDetail> imageUpload(String clientCode, String uri, FilePart fp, String fileName, Boolean override,
			ImageDetails imageDetails, String filePath, Boolean overrideImage) {
		boolean ovr = override == null || override.booleanValue();
		if (uri.indexOf(TRANSFORM_TYPE) != -1) {
			int ind = uri.indexOf(TRANSFORM_TYPE);
			uri = uri.substring(0, ind) + uri.substring(ind + TRANSFORM_TYPE.length() + 1);
		}
		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
		String resourcePath = tup.getT1();
		String urlResourcePath = tup.getT2();
		
		return FlatMapUtil.flatMapMonoWithNull(

				() -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),

				hasPermission -> {
					if (!hasPermission.booleanValue())
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);

					Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

					return this.createOrGetPath(path, urlResourcePath, fp, fileName, ovr);
				},

				(hasPermission, path) -> {
					Path[] file = new Path[1];

					if (path != null) {
						file[0] = path;
					}

					if (fp == null) {
						if (overrideImage) {
							file[0] = Paths.get(this.getBaseLocation(),
									this.resolvePathWithClientCode(filePath).getT1());
						} else {
							return Mono.just(this.convertToFileDetailWhileCreation(urlResourcePath, clientCode,
									file[0].toFile()));
						}
					}

					String fileType = null;

					try {
						fileType = Files.probeContentType(file[0]);
					} catch (IOException e2) {
						return Mono.empty();
					}

					if (fileType == null || !fileType.startsWith("image/"))
						return Mono.empty();

					return FlatMapUtil.flatMapMonoWithNull(

							() -> {
								if (fp != null) {
									return fp.transferTo(file[0]);
								} else {
									return Mono.empty();
								}
							},

							x -> {
								return Mono.just(file[0].toFile());
							},

							(x, actualFile) -> {
								try {
									BufferedImage bufferedImage = ImageIO.read(actualFile);

									BufferedImage resizedImage = resizeImage(bufferedImage, imageDetails.getWidth(),
											imageDetails.getHeight());

									BufferedImage flippedImage = resizedImage;
									if (imageDetails.getFlipHorizontal().booleanValue()) {
										BufferedImage flippedHorizontal = flipHorizontal(flippedImage);
										flippedImage = null;
										flippedImage = flippedHorizontal;
									}
									if (imageDetails.getFlipVertical().booleanValue()) {
										BufferedImage flippedVertical = flipVertical(flippedImage);
										flippedImage = null;
										flippedImage = flippedVertical;
									}

									BufferedImage rotatedImage = rotateImage(flippedImage, imageDetails.getRotation(),
											imageDetails.getBackgroundColor(), actualFile);

									BufferedImage croppedImage = rotatedImage;
									if (imageDetails.getCropAreaWidth() > 0 && imageDetails.getCropAreaHeight() > 0) {
										croppedImage = cropImage(rotatedImage, imageDetails.getXAxis(),
												imageDetails.getYAxis(), imageDetails.getCropAreaWidth(),
												imageDetails.getCropAreaHeight());
									}

									return Mono.just(croppedImage);
								} catch (IOException e) {
									return Mono.empty();
								}

							},

							(x, actualFile, updatedFile) -> {

								try {
									String imageName = actualFile.getName();
									int lastDotIndex = imageName.lastIndexOf('.');
									String imageExtension = imageName.substring(lastDotIndex + 1).toLowerCase();
									
									String newImageName = fileName + "." + imageExtension;
									
									if (!overrideImage) {
							            for (int i = 1; i <= 100; i++) {
							                newImageName = fileName + "" + i + "." + imageExtension;

							                boolean fileExists = checkIfNameExistsInFileSystem(newImageName, actualFile.getParentFile());

							                if (!fileExists) {
							                    break;
							                }
							            }
							        }

									File updatedFileWithNewName = new File(actualFile.getParent(), newImageName);
									ImageIO.write(updatedFile, imageExtension, updatedFileWithNewName);
									
									return Mono.just(this.convertToFileDetailWhileCreation(urlResourcePath, clientCode,
											file[0].toFile()));

								} catch (IOException e) {
									return Mono.empty();
								}

							})
							.switchIfEmpty(
									msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
											FilesMessageResourceService.IMAGE_TRANSFORM_ERROR))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));

				})
				.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						FilesMessageResourceService.IMAGE_FILE_REQUIRED))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
	}
	
	private boolean checkIfNameExistsInFileSystem(String newImageName, File directory) {
	    Path filePath = Paths.get(directory.getAbsolutePath(), newImageName);
	    return Files.exists(filePath);
	}

    public BufferedImage rotateImage(BufferedImage originalImage, double angle, String backgroundColor, File file) {
    	int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        double radians = Math.toRadians(angle);
        double rotatedWidth = Math.abs(Math.sin(radians) * height) + Math.abs(Math.cos(radians) * width);
        double rotatedHeight = Math.abs(Math.sin(radians) * width) + Math.abs(Math.cos(radians) * height);
        
        String fileExtension = getFileExtension(file);
        BufferedImage rotatedImage;
        
        if(fileExtension.equals("png")) {
        	rotatedImage = new BufferedImage((int) rotatedWidth, (int) rotatedHeight, BufferedImage.TYPE_INT_ARGB);
        } else {
        	rotatedImage = new BufferedImage((int) rotatedWidth, (int) rotatedHeight, originalImage.getType());
        }

        Graphics2D g2d = rotatedImage.createGraphics();
        
        Color color = getColorFromString(backgroundColor);
        g2d.setColor(color);
        g2d.fillRect(0, 0, rotatedImage.getWidth(), rotatedImage.getHeight());

        AffineTransform transform = new AffineTransform();
        transform.rotate(radians, rotatedWidth / 2, rotatedHeight / 2);

        g2d.setTransform(transform);
        int x = (int) ((rotatedWidth - width) / 2);
        int y = (int) ((rotatedHeight - height) / 2);
        g2d.drawImage(originalImage, x, y, null);
        g2d.dispose();

        return rotatedImage;
    }
	
	public BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
		Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
		BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
	}
	
    public BufferedImage cropImage(BufferedImage originalImage, int xAxis, int yAxis, int width, int height) {
        return originalImage.getSubimage(xAxis, yAxis, width, height);
    }
    
    public BufferedImage flipHorizontal(BufferedImage originalImage) {
        AffineTransform tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-originalImage.getWidth(null), 0);

        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(originalImage, null);
    }

    public BufferedImage flipVertical(BufferedImage originalImage) {
        AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
        tx.translate(0, -originalImage.getHeight(null));

        AffineTransformOp op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(originalImage, null);
    }
    
    private static String getFileExtension(File file) {
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf('.');
        
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            return fileName.substring(lastDotIndex + 1).toLowerCase();
        } else {
            return "";
        }
    }
    
    public static Color getColorFromString(String colorString) {
        if (colorString == null || colorString.trim().length()<=3 || colorString.trim().isEmpty()) {
            return new Color(0, 0, 0, 0);
        }
        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);
        }
        
        if(colorString.length()<=6) {
          int rgbValue = Integer.parseInt(colorString, 16);
          return new Color(rgbValue);
        }
        
        int rgbValue = Integer.parseInt(colorString.substring(0, 6), 16);
        int alphaValue = Integer.parseInt(colorString.substring(6), 16);
        
        int r = (rgbValue >> 16) & 0xFF;
        int g = (rgbValue >> 8) & 0xFF;
        int b = rgbValue & 0xFF;

        return new Color(r, g, b, alphaValue);
    }

	public Mono<Boolean> createFromZipFile(String clientCode, String uri, FilePart fp, Boolean override) {

		boolean ovr = override == null || override.booleanValue();
		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPartImport, uri);
		String resourcePath = tup.getT1();

		if (fp == null || (!fp.filename()
				.toLowerCase()
				.endsWith(".zip"))) {
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					FilesMessageResourceService.UNABLE_TO_READ_UP_FILE);
		}

		return FlatMapUtil.flatMapMono(

				() -> {
					Path tmpFile;
					Path tmpFolder;
					try {
						tmpFile = Files.createTempFile("tmp", "zip");
						tmpFolder = Files.createTempDirectory("tmp");
					} catch (IOException e) {
						return Mono.error(e);
					}
					return fp.transferTo(tmpFile)
							.then(Mono.just(Tuples.of(tmpFile, tmpFolder)));
				},

				tmpTup -> FlatMapUtil.flatMapFlux(

						() -> this.deflate(tmpTup.getT1(), tmpTup.getT2()),

						eFile -> Flux.from(this.fileAccessService.hasWriteAccess(
								this.parentOf(resourcePath + eFile.getT1()), clientCode, this.getResourceType())),

						(eFile, hasPermission) -> {
							if (!hasPermission.booleanValue())
								return Flux.empty();

							Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath, eFile.getT1());
							try {

								Files.createDirectories(path.getParent());
								if (ovr)
									Files.move(eFile.getT2(), path, StandardCopyOption.REPLACE_EXISTING);
								else
									Files.move(eFile.getT2(), path);
							} catch (IOException ex) {
								logger.debug("Ignoring exception while moving files after extracting.", ex);
							}

							return Flux.just(true);
						})
						.collectList()
						.map(e -> true))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.createFromZipFile"))
				.map(e -> true)
				.subscribeOn(Schedulers.boundedElastic());
	}

	private String parentOf(String name) {
		int ind = name.lastIndexOf('/');
		if (ind == -1)
			return name;
		return name.substring(0, ind);
	}

	private Flux<Tuple2<String, Path>> deflate(Path tmpFile, Path tmpFolder) {

		List<Tuple2<String, Path>> files = new ArrayList<>();

		try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tmpFile.toFile()))) {

			ZipEntry ze = null;

			while ((ze = zis.getNextEntry()) != null) {

				if (ze.isDirectory()) {
					Files.createDirectories(tmpFolder.resolve(ze.getName()));
				} else {
					var file = tmpFolder.resolve(ze.getName());
					files.add(Tuples.of(ze.getName(), file));
					Files.createDirectories(file.getParent());
					try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
						int len;
						byte[] buffer = new byte[1024];
						while ((len = zis.read(buffer)) > 0) {
							fos.write(buffer, 0, len);
						}
					}
				}

			}
		} catch (IOException e) {

			return this.msgService.throwFluxMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, e),
					FilesMessageResourceService.UNABLE_TO_READ_UP_FILE);
		}

		return Flux.fromIterable(files);
	}

	protected Tuple2<String, String> resolvePathWithClientCode(String uri) {

		String path = uri.substring(uri.indexOf(this.uriPartFile) + this.uriPartFile.length());
		String origPath = path;

		path = URLDecoder.decode(path, StandardCharsets.UTF_8)
				.replace('+', ' ');

		int index = path.indexOf('?');
		if (index != -1)
			path = path.substring(0, index);

		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);

		return Tuples.of(path, origPath);
	}

	private Tuple2<String, String> resolvePathWithoutClientCode(String part, String uri) {

		String path = uri.substring(uri.indexOf(part) + part.length(), uri.length() - (uri.endsWith("/") ? 1 : 0));
		String origPath = path;

		path = URLDecoder.decode(path, StandardCharsets.UTF_8)
				.replace('+', ' ');

		int index = path.indexOf('?');
		if (index != -1)
			path = path.substring(0, index);

		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);

		return Tuples.of(path, origPath);
	}

	private Mono<Path> createOrGetPath(Path path, String resourcePath, FilePart fp, String fileName, boolean ovr) {

		if (!Files.exists(path))
			try {
				Files.createDirectories(path);
			} catch (IOException e) {
				return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
						FilesMessageResourceService.PATH_NOT_FOUND,
						resourcePath);
			}

		if (fp == null)
			return Mono.just(path);

		if (!Files.isDirectory(path))
			return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					FilesMessageResourceService.NOT_A_DIRECTORY,
					resourcePath);

		Path file = path.resolve(
				fileName == null ? fp.filename() : FileExtensionUtil.getFileNameWithExtension(fp.filename(), fileName));

		if (Files.exists(file) && !ovr)
			return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					FilesMessageResourceService.ALREADY_EXISTS, "File", file.getFileName());

		return Mono.just(file);
	}

	public abstract String getBaseLocation();

	public abstract FilesAccessPathResourceType getResourceType();
}
