package com.fincity.saas.files.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

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
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.util.FileExtensionUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public abstract class AbstractFilesResourceService {

	private static final String GENERIC_URI_PART = "api/files/";

	private static final String GENERIC_URI_PART_FILE = "/file";

	private static Logger logger = LoggerFactory.getLogger(AbstractFilesResourceService.class);

	@Autowired
	private FilesMessageResourceService msgService;

	@Autowired
	protected FilesAccessPathService fileAccessService;

	private String uriPart;
	private String uriPartFile;
	private int uriPartLength;
	private int uriPartFileLength;

	@PostConstruct
	private void initialize() {

		String type = this.getResourceType()
		        .toString()
		        .toLowerCase();

		this.uriPart = GENERIC_URI_PART + type;
		this.uriPartLength = this.uriPart.length();

		this.uriPartFile = GENERIC_URI_PART + type + GENERIC_URI_PART_FILE;
		this.uriPartFileLength = this.uriPartFile.length();
	}

	private static final Map<String, Comparator<File>> COMPARATORS = new HashMap<>(Map.of(

	        "TYPE",
	        Comparator.<File, String>comparing(e -> e.isDirectory() ? " " : FileExtensionUtil.get(e.getName()),
	                String.CASE_INSENSITIVE_ORDER),

	        "SIZE", Comparator.comparingLong(File::length),

	        "NAME", Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER),

	        "LASTMODIFIED", Comparator.comparingLong(File::lastModified)));

	public Mono<Page<FileDetail>> list(String clientCode, String uri, String filter, Pageable page) {

		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(uri);
		String resourcePath = tup.getT1();
		String urlResourcePath = tup.getT2();

		return FlatMapUtil.flatMapMono(

		        () -> this.fileAccessService.hasReadAccess(resourcePath, clientCode, this.getResourceType()),

		        hasPermission ->
				{

			        if (!hasPermission.booleanValue())
				        return msgService.throwMessage(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PATH,
				                this.getResourceType(), resourcePath);

			        Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

			        if (!Files.exists(path))
				        this.msgService.throwMessage(HttpStatus.NOT_FOUND, FilesMessageResourceService.PATH_NOT_FOUND,
				                resourcePath);

			        if (!Files.isDirectory(path))
				        return msgService.throwMessage(HttpStatus.BAD_REQUEST,
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

				        String stringNameFilter = nameFilter;

				        return Flux.fromStream(stream)
				                .filter(e -> !e.equals(path))
				                .map(Path::toFile)
				                .filter(obj -> obj.getName()
				                        .toUpperCase()
				                        .contains(stringNameFilter))
				                .sort(sortComparator)
				                .map(e -> this.convertToFileDetail(urlResourcePath, clientCode, e))
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

			logger.debug("Unable to read attributes of file {} ", file.getAbsolutePath(), e);
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

		        (rp, hasAccess) ->
				{

			        if (!hasAccess.booleanValue())
				        return this.msgService.throwMessage(HttpStatus.FORBIDDEN,
				                FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), rp);

			        Path file = Paths.get(this.getBaseLocation(), rp);

			        if (!Files.exists(file))
				        return this.msgService.throwMessage(HttpStatus.NOT_FOUND,
				                FilesMessageResourceService.PATH_NOT_FOUND, rp);

			        long fileMillis = -1;
			        try {

				        BasicFileAttributes attr = Files.readAttributes(file, BasicFileAttributes.class);
				        fileMillis = attr.lastModifiedTime()
				                .toMillis();
			        } catch (IOException e) {

				        logger.debug("Unable to read attributes of file {} ", file, e);
			        }

			        String fileETag = new StringBuilder().append('"')
			                .append(file.hashCode())
			                .append('-')
			                .append(fileMillis)
			                .append('-')
			                .append(downloadOptions.eTagCode())
			                .append('"')
			                .toString();

			        return makeMatchesStartDownload(downloadOptions, request, response, file, fileMillis, fileETag);
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

		respHeaders.set("x-cache", "MISS");
		respHeaders.setLastModified(fileMillis);
		respHeaders.setETag(eTag);
		if (!downloadOptions.getNoCache()
		        .booleanValue())
			respHeaders.setCacheControl("public, max-age=3600, must-revalidate");
		respHeaders.setContentDisposition((downloadOptions.getDownload()
		        .booleanValue() ? ContentDisposition.attachment() : ContentDisposition.inline())
		        .filename(file.getFileName()
		                .toString())
		        .build());
		String mimeType = URLConnection.guessContentTypeFromName(file.getFileName()
		        .toString());
		if (mimeType == null) {
			logger.debug("Unable to find mimetype of file {}", file.toAbsolutePath());
			mimeType = "application/octet-stream";
		}
		respHeaders.setContentType(MediaType.valueOf(mimeType));

		File actualFile = file.toFile();

		long length = actualFile.length();

		List<HttpRange> ranges = request.getHeaders()
		        .getRange();

		if (ranges.isEmpty()) {

			return sendFileWhenNoRanges(downloadOptions, file, response, respHeaders, actualFile, length);
		} else {

			return sendFileWhenRanges(downloadOptions, request, response, actualFile);
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
		response.setStatusCode(HttpStatus.OK);
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

		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(uri);
		String resourcePath = tup.getT1();

		return FlatMapUtil.flatMapMono(

		        () -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),

		        hasPermission ->
				{

			        if (!hasPermission.booleanValue()) {
				        return this.msgService.throwMessage(HttpStatus.FORBIDDEN,
				                FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);
			        }

			        Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

			        if (Files.isDirectory(path)) {

				        try {
					        return Mono.just(FileSystemUtils.deleteRecursively(path));
				        } catch (IOException e) {
					        this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
					                FilesMessageResourceService.UNABLE_TO_DEL_FILE, path.toString());
				        }
			        } else {
				        try {
					        return Mono.just(Files.deleteIfExists(path));
				        } catch (IOException e) {
					        this.msgService.throwMessage(HttpStatus.INTERNAL_SERVER_ERROR,
					                FilesMessageResourceService.UNABLE_TO_DEL_FILE, path.toString());
				        }
			        }

			        return Mono.just(true);
		        }

		);
	}

	public Mono<Boolean> createFolder(String clientCode, String uri, String folderName) {

		String folder = "/folderCreate";
		int index = uri.indexOf(folder);

		String removeRequestFromURI = uri.substring(0, index)
		        + uri.substring(index + folder.length(), uri.indexOf('?'));

		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(removeRequestFromURI);
		String resourcePath = tup.getT1();

		return FlatMapUtil.flatMapMono(
		        () -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),

		        hasPermission ->
				{
			        if (!hasPermission.booleanValue())
				        return msgService.throwMessage(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PATH,
				                this.getResourceType(), resourcePath);

			        Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath, folderName);

			        if (!Files.exists(path))
				        try {
					        Files.createDirectories(path);
					        return Mono.just(true);
				        } catch (Exception ex) {
					        return this.msgService.throwMessage(HttpStatus.FORBIDDEN,
					                FilesMessageResourceService.FOLDER_CREATION_ERROR);
				        }

			        return this.msgService.throwMessage(HttpStatus.FORBIDDEN,
			                FilesMessageResourceService.ALREADY_EXISTS, "folder with name ", folderName);
		        });
	}

	public Mono<FileDetail> create(String clientCode, String uri, FilePart fp, String fileName, Boolean override) {

		boolean ovr = override == null || override.booleanValue();
		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(uri);
		String resourcePath = tup.getT1();
		String urlResourcePath = tup.getT2();

		return FlatMapUtil.flatMapMonoWithNull(

		        () -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),

		        hasPermission ->
				{

			        if (!hasPermission.booleanValue())
				        return msgService.throwMessage(HttpStatus.FORBIDDEN, FilesMessageResourceService.FORBIDDEN_PATH,
				                this.getResourceType(), resourcePath);

			        Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);

			        if (!Files.exists(path))
				        try {
					        Files.createDirectories(path);
				        } catch (IOException e) {
					        return this.msgService.throwMessage(HttpStatus.NOT_FOUND,
					                FilesMessageResourceService.PATH_NOT_FOUND, resourcePath);
				        }

			        if (!Files.isDirectory(path))
				        return msgService.throwMessage(HttpStatus.BAD_REQUEST,
				                FilesMessageResourceService.NOT_A_DIRECTORY, resourcePath);

			        Path file = path.resolve(fileName == null ? fp.filename()
			                : FileExtensionUtil.getFileNameWithExtension(fp.filename(), fileName));

			        if (Files.exists(file) && !ovr)
				        return this.msgService.throwMessage(HttpStatus.BAD_REQUEST,
				                FilesMessageResourceService.ALREADY_EXISTS, "File", file.getFileName());

			        return Mono.just(file);

		        },

		        (hasPermission, file) -> fp.transferTo(file),

		        (hasPermission, file, fil) -> Mono
		                .just(this.convertToFileDetail(urlResourcePath, clientCode, file.toFile())));
	}

	protected Tuple2<String, String> resolvePathWithClientCode(String uri) {

		String path = uri.substring(uri.indexOf(this.uriPartFile) + this.uriPartFileLength);
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

	private Tuple2<String, String> resolvePathWithoutClientCode(String uri) {

		String path = uri.substring(uri.indexOf(this.uriPart) + this.uriPartLength,
		        uri.length() - (uri.endsWith("/") ? 1 : 0));
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

	public abstract String getBaseLocation();

	public abstract FilesAccessPathResourceType getResourceType();
}
