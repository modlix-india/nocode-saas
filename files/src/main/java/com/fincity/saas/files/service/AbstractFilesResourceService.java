package com.fincity.saas.files.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.imgscalr.Scalr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.FileType;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.util.FileExtensionUtil;
import com.fincity.saas.files.util.ImageTransformUtil;

import jakarta.annotation.PostConstruct;
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

	protected final FilesMessageResourceService msgService;
	protected final FilesAccessPathService fileAccessService;

	protected AbstractFilesResourceService(
			FilesAccessPathService fileAccessService, FilesMessageResourceService msgService) {
		this.msgService = msgService;
		this.fileAccessService = fileAccessService;
	}

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
			Comparator.<File, String>comparing(e -> e.isDirectory() ? " " : FileExtensionUtil.getExtension(e.getName()),
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
						return Mono.just(List.of());

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

		if (fileType == null)
			return e -> true;

		final Set<String> fileTypesSet = this.getFileExtensionsToFilter(fileType);

		Predicate<FileDetail> pr = fileTypesSet.isEmpty() ? null : e -> fileTypesSet.contains(e.getType());
		for (FileType ft : fileType) {
			if (ft == FileType.DIRECTORIES)
				pr = pr == null ? FileDetail::isDirectory : pr.or(FileDetail::isDirectory);
			else if (ft == FileType.FILES)
				pr = pr == null ? e -> !e.isDirectory() : pr.or(e -> !e.isDirectory());
		}

		return pr == null ? e -> true : pr;
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

	private Set<String> getFileExtensionsToFilter(FileType[] fileType) {

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
			damFile.setFilePath(resourcePath + "/" + file.getName())
					.setUrl(resourceType + ("/file/" + clientCode) + resourcePath + "/"
							+ URLEncoder.encode(file.getName(), StandardCharsets.UTF_8));
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
				.setFilePath(resourcePath + "/" + file.getName())
				.setUrl(resourceType + (file.isDirectory() ? "" : "/file/" + clientCode) + resourcePath + "/"
						+ URLEncoder.encode(file.getName(), StandardCharsets.UTF_8))
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
		if (!downloadOptions.getNoCache().booleanValue()
				&& this.getResourceType().equals(FilesAccessPathResourceType.STATIC))
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

		Path tmpFolder = Files.createTempDirectory("tmp");
		Path tmpFile = tmpFolder.resolve("directory.zip");
		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + tmpFile.toUri().toString()),
				Map.of("create", "true"));) {
			Files.walk(file)
					.forEach(e -> {
						try {
							System.out.println("Copying file " + e);
							if (Files.isDirectory(e)) {
								Files.createDirectories(fs.getPath("/" + file.relativize(e)
										.toString()));
								return;
							}
							if (Files.isHidden(e))
								return;
							Files.copy(e, fs.getPath("/" + file.relativize(e)
									.toString()), StandardCopyOption.REPLACE_EXISTING);
						} catch (IOException ex) {
							logger.debug("Unable to copy file {} to zip file", e, ex);
						}
					});
		}

		return tmpFile;
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

					String fn = fileName == null ? fp.filename()
							: FileExtensionUtil.getFileNameWithExtension(fp.filename(), fileName);
					return FlatMapUtil
							.flatMapMonoWithNull(() -> fp.transferTo(file.resolve(fn)),
									x -> Mono.just(this.convertToFileDetailWhileCreation(urlResourcePath, clientCode,
											file.resolve(fn).toFile())))
							.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
	}

	private static record PathParts(String resourcePath, String clientCode, String fileName) {
	}

	private PathParts extractPathClientCodeFileName(String uri, FilePart fp, String filePath,
			String clientCode, String fileName) {
		String resourcePath;

		if (fp != null) {
			resourcePath = this.resolvePathWithoutClientCode(this.uriPart, uri).getT1();
			fileName = fp.filename();
		} else {
			resourcePath = this.resolvePathWithClientCode(filePath).getT1();
			String[] pathParts = resourcePath.split("/");
			for (String pathPart : pathParts) {
				if (!StringUtil.safeIsBlank(pathPart)) {
					clientCode = pathPart;
					break;
				}
			}

			for (int i = pathParts.length - 1; i >= 0; i--) {
				if (!StringUtil.safeIsBlank(pathParts[i])) {
					fileName = pathParts[i];
					break;
				}
			}

			// Removing the client code from the resource path
			resourcePath = resourcePath.substring(resourcePath.indexOf(clientCode) + clientCode.length());
			// Removing the file name from the resource path
			resourcePath = resourcePath.substring(0, resourcePath.lastIndexOf(fileName));
		}

		return new PathParts(resourcePath, clientCode, fileName);
	}

	public Mono<FileDetail> imageUpload(String clientCode, String uri, FilePart fp, String fileName, Boolean override,
			ImageDetails imageDetails, String filePath) {
		boolean ovr = override == null || override.booleanValue();

		// just removing the TRANSFORM_TYPE from the uri
		if (uri.indexOf(TRANSFORM_TYPE) != -1) {
			int ind = uri.indexOf(TRANSFORM_TYPE);
			uri = uri.substring(0, ind) + uri.substring(ind + TRANSFORM_TYPE.length() + 1);
		}

		PathParts pathParts = this.extractPathClientCodeFileName(uri, fp, filePath,
				clientCode, fileName);

		return FlatMapUtil.flatMapMono(

				() -> this.fileAccessService.hasWriteAccess(pathParts.resourcePath, pathParts.clientCode,
						getResourceType()),

				hasPermission -> {
					if (!hasPermission.booleanValue())
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, getResourceType(), pathParts.fileName);

					Path path = Paths.get(this.getBaseLocation(), pathParts.clientCode, pathParts.resourcePath);

					return this.createOrGetPath(path, pathParts.resourcePath, fp,
							fp == null ? pathParts.fileName : null, ovr);
				},

				(hasPermission, relativePath) -> this.makeSourceImage(fp, pathParts),

				(hasPermission, relativePath, sourceTuple) -> {

					int type = sourceTuple.getT2();

					if (fp != null && !StringUtil.safeIsBlank(fileName)) {
						type = fileName.toLowerCase().endsWith("png") ? BufferedImage.TYPE_INT_ARGB
								: BufferedImage.TYPE_INT_RGB;
					}

					final int finalImageType = type;
					return Mono
							.defer(() -> Mono.just(Tuples.of(
									ImageTransformUtil.transformImage(sourceTuple.getT1(), finalImageType,
											imageDetails),
									finalImageType)))
							.subscribeOn(Schedulers.boundedElastic());
				},

				(hasPermission, relativePath, sourceTuple, transformedTuple) -> finalFileWrite(fp, fileName, pathParts,
						relativePath, transformedTuple)

		).switchIfEmpty(this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				FilesMessageResourceService.IMAGE_TRANSFORM_ERROR))
				.contextWrite(Context.of(LogUtil.METHOD_NAME,
						"AbstractFilesResourceService.imageUpload"));
	}

	private Mono<FileDetail> finalFileWrite(FilePart fp, String fileName, PathParts pathParts, Path relativePath,
			Tuple2<BufferedImage, Integer> transformedTuple) {
		Path path = relativePath
				.resolve(CommonsUtil.nonNullValue(StringUtil.safeIsBlank(fileName) ? null : fileName,
						fp != null ? fp.filename() : null, pathParts.fileName));

		try {
			ImageIO.write(transformedTuple.getT1(), path.getFileName()
					.toString()
					.toLowerCase()
					.endsWith("png") ? "png" : "jpeg", path.toFile());
		} catch (IOException e) {
			return this.msgService.throwMessage(
					msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
					FilesMessageResourceService.IMAGE_TRANSFORM_ERROR);
		}

		return Mono.just(
				this.convertToFileDetailWhileCreation(pathParts.resourcePath,
						pathParts.clientCode,
						path.toFile()));
	}

	private Mono<Tuple2<BufferedImage, Integer>> makeSourceImage(FilePart fp,
			PathParts pathParts) {

		try {
			ImageInputStream iis;
			if (fp != null)
				iis = ImageIO.createImageInputStream(getInputStreamFromFluxDataBuffer(fp.content()));
			else
				iis = ImageIO.createImageInputStream(
						Paths.get(this.getBaseLocation(), pathParts.clientCode, pathParts.resourcePath,
								pathParts.fileName).toFile());

			Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(iis);

			while (imageReaders.hasNext()) {
				ImageReader reader = imageReaders.next();
				reader.setInput(iis);
				return Mono.just(Tuples.of(reader.read(0),
						pathParts.fileName.toLowerCase().endsWith("png") ? BufferedImage.TYPE_INT_ARGB
								: BufferedImage.TYPE_INT_RGB));
			}
		} catch (IOException e) {
			return this.msgService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg, e),
					FilesMessageResourceService.IMAGE_TRANSFORM_ERROR);
		}

		return this.msgService.throwMessage(
				msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
				FilesMessageResourceService.IMAGE_TRANSFORM_ERROR);
	}

	private InputStream getInputStreamFromFluxDataBuffer(Flux<DataBuffer> data) throws IOException {

		PipedOutputStream osPipe = new PipedOutputStream();// NOSONAR
		// Cannot be used in try-with-resource as this has to be part of Reactor and
		// don't know when this can be closed.
		// Since doOnComplete is used we are closing the resource after writing the
		// data.
		PipedInputStream isPipe = new PipedInputStream(osPipe);

		DataBufferUtils.write(data, osPipe)
				.subscribeOn(Schedulers.boundedElastic())
				.doOnComplete(() -> {
					try {
						osPipe.close();
					} catch (IOException ignored) {
						logger.debug("Issues with accessing buffer.", ignored);
					}
				})
				.subscribe(DataBufferUtils.releaseConsumer());
		return isPipe;

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

						eFile -> !Files.isDirectory(eFile.getT2()) ? Flux.just(true)
								: Flux.from(this.fileAccessService.hasWriteAccess(
										this.parentOf(resourcePath + eFile.getT1()), clientCode,
										this.getResourceType())),

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

		path = URLDecoder.decode(path.replace('+', ' '), StandardCharsets.UTF_8);

		int index = path.indexOf('?');
		if (index != -1)
			path = path.substring(0, index);

		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);

		return Tuples.of(path, origPath);
	}

	protected Tuple2<String, String> resolvePathWithoutClientCode(String part, String uri) {

		String path = uri.substring(uri.indexOf(part) + part.length(), uri.length() - (uri.endsWith("/") ? 1 : 0));
		String origPath = path;

		path = URLDecoder.decode(path.replace('+', ' '), StandardCharsets.UTF_8);

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

		return Mono.just(path);
	}

	public Mono<FileDetail> createInternal(String clientCode, boolean override, String filePath,
			String fileName, ServerHttpRequest request) {

		Mono<FileDetail> fd = FlatMapUtil.flatMapMono(

				() -> Mono.just(Paths.get(this.getBaseLocation(), clientCode,
						StringUtil.safeIsBlank(filePath) ? "/" : filePath)),

				path -> {

					if (!Files.exists(path))
						try {
							Files.createDirectories(path);
						} catch (IOException e) {
							return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
									FilesMessageResourceService.PATH_NOT_FOUND, filePath);
						}

					if (!Files.isDirectory(path))
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								FilesMessageResourceService.NOT_A_DIRECTORY, filePath);

					Path file = path.resolve(fileName);

					if (Files.exists(file) && !override)
						return this.msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								FilesMessageResourceService.ALREADY_EXISTS, "File", file.getFileName());

					return Mono.just(file);
				},

				(path, filePathPath) -> DataBufferUtils
						.write(request.getBody(), filePathPath, StandardOpenOption.CREATE,
								StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
						.then(Mono.just(filePathPath))
						.flatMap(x -> {
							try {
								return Mono.just(this.convertToFileDetailWhileCreation(filePath, clientCode,
										filePathPath.toFile()));
							} catch (Exception e) {
								return this.msgService.throwMessage(
										msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg, e),
										FilesMessageResourceService.UNKNOWN_ERROR);
							}
						})

		);

		return fd.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.createInternal"));
	}

	public abstract String getBaseLocation();

	public abstract FilesAccessPathResourceType getResourceType();

}
