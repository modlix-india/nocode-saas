package com.fincity.saas.files.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.files.jooq.enums.FilesAccessPathResourceType;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.model.ImageDetails;
import com.fincity.saas.files.util.FileExtensionUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class TransformStaticImageService {
	
	@Autowired
	protected FilesAccessPathService fileAccessService;
	
	@Autowired
	private FilesMessageResourceService msgService;
	
	private static final String GENERIC_URI_PART = "api/files/";
	
	private String uriPart;
	
	@PostConstruct
	private void initialize() {
		String type = this.getResourceType()
				.toString()
				.toLowerCase();
		
		this.uriPart = GENERIC_URI_PART + type;
	}
	
	@Value("${files.resources.location.static}")
	private String location;

	private String staticResourceLocation;

	@PostConstruct
	private void initializeStatic() {
		this.staticResourceLocation = location;
	}
	
	
	public Mono<FileDetail> create(String clientCode, String uri, FilePart fp, String fileName, Boolean override, ImageDetails imageDetails) {

		boolean ovr = override == null || override.booleanValue();
		Tuple2<String, String> tup = this.resolvePathWithoutClientCode(this.uriPart, uri);
		String resourcePath = tup.getT1();
		String urlResourcePath = tup.getT2();
		
		return FlatMapUtil.flatMapMono(
				() -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()), 
				hasPermission -> {
					if(!hasPermission.booleanValue())
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);
					
					Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);
					return this.createOrGetPath(path, urlResourcePath, fp, fileName, ovr);
				}, 
				(hasPermission, file) -> {
					
					if(fp==null) {
						System.out.println("null");
					}else {
						System.out.println("hello test");
					}
					if (fp == null)
//						return Mono.just(
//								this.convertToFileDetailWhileCreation(urlResourcePath, clientCode, file.toFile()));
						return null;
					
					String fileType = null;
//					
					try {
						fileType = Files.probeContentType(file);
					} catch (Exception e2) {
						e2.printStackTrace();
					}
					System.out.println("file type "+fileType);
					
//					if (fp != null && fileType != null && fileType.startsWith("image/")) {
//
//						 FlatMapUtil.flatMapMonoWithNull(
//
//								() -> fp.transferTo(file),
//
//								x -> Mono.just(file.toFile()),
//
//								(x, actualFile) -> {
//									try {
//										BufferedImage bufferedImage = ImageIO.read(actualFile);
//										BufferedImage resizedImage = resizeImage(bufferedImage, imageDetails.getWidth(), imageDetails.getHeight());
//										BufferedImage croppedImage = cropImage(resizedImage, imageDetails.getXAsix(), imageDetails.getYAxis(), imageDetails.getCropAreaWidth(), imageDetails.getCropAreaHeight());
//										BufferedImage rotatedImage = rotateImage(croppedImage, imageDetails.getRotation());
//										return Mono.just(rotatedImage);
//									} catch (IOException e) {
//										e.printStackTrace();
//									}
//									return null;
//								},
//								
//								(x, actualFile, updatedFile) -> {
//									try {
//										String imageName = actualFile.getName();
//								        int lastDotIndex = imageName.lastIndexOf('.');
//								        String imageExtension = imageName.substring(lastDotIndex + 1).toLowerCase();
//										FileOutputStream fos = new FileOutputStream(actualFile);
//										OutputStream os = new FileOutputStream(actualFile);
////								        Boolean isDone = ImageIO.write(updatedFile, imageExtension, os);
//										Boolean isDone = ImageIO.write(updatedFile, imageExtension, actualFile);
//										for(var a : ImageIO.getWriterFormatNames()) {
//											System.out.println("a "+a);
//										}
//										System.out.println("is done "+isDone);
//									} catch (IOException e) {
////									} catch (Exception e) {
//										e.printStackTrace();
//									}
//									return Mono.just(this.convertToFileDetailWhileCreation(
//										urlResourcePath, clientCode,
//										file.toFile()));
//									}
//								)
//								.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
//						 
//						 return null;
//
//					}
					System.out.println("here i am");

//					return FlatMapUtil.flatMapMonoWithNull(
//									() -> fp.transferTo(file),
//									x -> Mono.just(
//											this.convertToFileDetailWhileCreation(urlResourcePath, clientCode,file.toFile())
//									)
//								)
//							.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
					return null;
				}, 
				null, 
				null, 
				null, 
				null, 
				null, 
				null, 
				null);

//		return FlatMapUtil.flatMapMonoWithNull(
//
//				() -> this.fileAccessService.hasWriteAccess(resourcePath, clientCode, this.getResourceType()),
//
//				hasPermission -> {
//
//					if (!hasPermission.booleanValue())
//						return msgService.throwMessage(msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
//								FilesMessageResourceService.FORBIDDEN_PATH, this.getResourceType(), resourcePath);
//
//					Path path = Paths.get(this.getBaseLocation(), clientCode, resourcePath);
//
//					return this.createOrGetPath(path, urlResourcePath, fp, fileName, ovr);
//				},
//
//				(hasPermission, file) -> {
//					
//					if(fp==null) {
//						System.out.println("null");
//					}else {
//						System.out.println("hello test");
//					}
//
//					if (fp == null)
//						return Mono.just(
//								this.convertToFileDetailWhileCreation(urlResourcePath, clientCode, file.toFile()));
//					
//					String fileType = null;
//					
//					try {
//						fileType = Files.probeContentType(file);
//					} catch (Exception e2) {
//						e2.printStackTrace();
//					}
//					System.out.println("file type "+fileType);
//					
//					if (fp != null && fileType != null && fileType.startsWith("image/")) {
//
//						return FlatMapUtil.flatMapMonoWithNull(
//
//								() -> fp.transferTo(file),
//
//								x -> Mono.just(file.toFile()),
//
//								(x, actualFile) -> {
//									try {
//										BufferedImage bufferedImage = ImageIO.read(actualFile);
//										BufferedImage resizedImage = resizeImage(bufferedImage, imageDetails.getWidth(), imageDetails.getHeight());
//										BufferedImage croppedImage = cropImage(resizedImage, imageDetails.getXAsix(), imageDetails.getYAxis(), imageDetails.getCropAreaWidth(), imageDetails.getCropAreaHeight());
//										BufferedImage rotatedImage = rotateImage(croppedImage, imageDetails.getRotation());
//										return Mono.just(rotatedImage);
//									} catch (IOException e) {
//										e.printStackTrace();
//									}
//									return null;
//								},
//								
//								(x, actualFile, updatedFile) -> {
//									try {
//										String imageName = actualFile.getName();
//								        int lastDotIndex = imageName.lastIndexOf('.');
//								        String imageExtension = imageName.substring(lastDotIndex + 1).toLowerCase();
//										FileOutputStream fos = new FileOutputStream(actualFile);
//										OutputStream os = new FileOutputStream(actualFile);
////								        Boolean isDone = ImageIO.write(updatedFile, imageExtension, os);
//										Boolean isDone = ImageIO.write(updatedFile, imageExtension, actualFile);
//										for(var a : ImageIO.getWriterFormatNames()) {
//											System.out.println("a "+a);
//										}
//										System.out.println("is done "+isDone);
//									} catch (IOException e) {
////									} catch (Exception e) {
//										e.printStackTrace();
//									}
//									return Mono.just(this.convertToFileDetailWhileCreation(
//										urlResourcePath, clientCode,
//										file.toFile()));
//									}
//								)
//								.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
//
//					}
//					System.out.println("here i am");
//
//					return FlatMapUtil.flatMapMonoWithNull(
//									() -> fp.transferTo(file),
//									x -> Mono.just(
//											this.convertToFileDetailWhileCreation(urlResourcePath, clientCode,file.toFile())
//									)
//								)
//							.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
//				})
//				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractFilesResourceService.create"));
	}
	
    public BufferedImage rotateImage(BufferedImage originalImage, double angle) {
    	int width = originalImage.getWidth();
        int height = originalImage.getHeight();
        
        double radians = Math.toRadians(angle);
        double rotatedWidth = Math.abs(Math.sin(radians) * height) + Math.abs(Math.cos(radians) * width);
        double rotatedHeight = Math.abs(Math.sin(radians) * width) + Math.abs(Math.cos(radians) * height);
        
        System.out.println("originalImage.getType() "+originalImage.getType());
        
        BufferedImage rotatedImage = new BufferedImage((int) rotatedWidth, (int) rotatedHeight, originalImage.getType());
//        BufferedImage rotatedImage = new BufferedImage((int) rotatedWidth, (int) rotatedHeight, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = rotatedImage.createGraphics();
        
        Color color = getColorFromString("");
        
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
//	    BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
//	    Graphics2D graphics2D = resizedImage.createGraphics();
//	    graphics2D.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
//	    graphics2D.dispose();
//	    return resizedImage;
		

		Image resultingImage = originalImage.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
		BufferedImage outputImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
		outputImage.getGraphics().drawImage(resultingImage, 0, 0, null);
		return outputImage;
		
		
//		BufferedImage resizedImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
//
//        Graphics2D g2d = resizedImage.createGraphics();
//        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
//        g2d.drawImage(originalImage, 0, 0, targetWidth, targetHeight, null);
//        g2d.dispose();
//
//        return resizedImage;
	}
	
    public BufferedImage cropImage(BufferedImage originalImage, int xAxis, int yAxis, int width, int height) {
        return originalImage.getSubimage(xAxis, yAxis, width, height);
    }
    
    public static Color getColorFromString(String colorString) {
        if (colorString == null || colorString.trim().length()<=3 || colorString.trim().isEmpty()) {
            return new Color(0, 0, 0, 0);
        }

        if (colorString.startsWith("#")) {
            colorString = colorString.substring(1);
        }

        int rgbValue = Integer.parseInt(colorString, 16);

        return new Color(rgbValue);
    
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
    
    public String getBaseLocation() {
		return this.staticResourceLocation;
	}
    
	public FilesAccessPathResourceType getResourceType() {
		return FilesAccessPathResourceType.STATIC;
	}
}
