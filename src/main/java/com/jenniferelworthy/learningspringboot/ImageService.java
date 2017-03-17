package com.jenniferelworthy.learningspringboot;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.actuate.metrics.CounterService;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.boot.actuate.metrics.repository.InMemoryMetricRepository;
import org.springframework.boot.actuate.metrics.writer.Delta;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author Greg Turnquist
 */
@Service
public class ImageService {

	private static String UPLOAD_ROOT = "upload-dir";

	private final ImageRepository repository;
	private final ResourceLoader resourceLoader;
	private final CounterService counterService;
	private final GaugeService gaugeService;
	private final InMemoryMetricRepository inMemoryMetricRepository;
	private final SimpMessagingTemplate messagingTemplate;
	private final UserRepository userRepository;

	@Autowired
	public ImageService(ImageRepository imageRepository, ResourceLoader resourceLoader, 
			CounterService counterService, GaugeService gaugeService, InMemoryMetricRepository inMemoryMetricRepository, 
			SimpMessagingTemplate messaginTemplate, UserRepository userRepository) {

		this.repository = imageRepository;
		this.resourceLoader = resourceLoader;
		this.counterService = counterService;
		this.gaugeService = gaugeService;
		this.inMemoryMetricRepository = inMemoryMetricRepository;
		this.messagingTemplate = messaginTemplate;
		this.userRepository = userRepository;
		
		this.counterService.reset("files.uploaded");
		this.gaugeService.submit("files.uploaded.lastBytes", 0);
		this.inMemoryMetricRepository.set(new Metric<Number>("files.uploaded.totalBytes", 0)); 
	}

	public Page<Image> findPage(Pageable pageable) {
		return repository.findAll(pageable);
	}

	public Resource findOneImage(String filename) {
		return resourceLoader.getResource("file:" + UPLOAD_ROOT + "/" + filename);
	}

	public void createImage(MultipartFile file) throws IOException {

		if (!file.isEmpty()) {
			Files.copy(file.getInputStream(), Paths.get(UPLOAD_ROOT, file.getOriginalFilename()));
			repository.save(new Image(
					file.getOriginalFilename(),
					userRepository.findByUsername(SecurityContextHolder.getContext().getAuthentication().getName())));
			counterService.increment("files.uploaded");
			gaugeService.submit("files.uploaded.lastBytes", file.getSize());
			inMemoryMetricRepository.increment(new Delta<Number>("files.uploaded.totalBytes", file.getSize()));
			messagingTemplate.convertAndSend("/topic/newImage", file.getOriginalFilename());
		}
	}

	public void deleteImage(String filename) throws IOException {

		final Image byName = repository.findByName(filename);
		repository.delete(byName);
		Files.deleteIfExists(Paths.get(UPLOAD_ROOT, filename));
		messagingTemplate.convertAndSend("/topic/deleteImage", filename);
	}

	/**
	 * Pre-load some fake images
	 *
	 * @return Spring Boot {@link CommandLineRunner} automatically run after app context is loaded.
	 */
	@Bean
	CommandLineRunner setUp(ImageRepository imageRepository,
							UserRepository userRepository) throws IOException {

		return (args) -> {
			FileSystemUtils.deleteRecursively(new File(UPLOAD_ROOT));

			Files.createDirectory(Paths.get(UPLOAD_ROOT));
			
			User jennifer = userRepository.save(new User("jennifer", "elworthy", "ROLE_ADMIN", "ROLE_USER"));
			User thomas = userRepository.save(new User("thomas", "elworthy", "ROLE_USER"));

			FileCopyUtils.copy("Test file", new FileWriter(UPLOAD_ROOT + "/test"));
			imageRepository.save(new Image("test", jennifer));

			FileCopyUtils.copy("Test file2", new FileWriter(UPLOAD_ROOT + "/test2"));
			imageRepository.save(new Image("test2", jennifer));

			FileCopyUtils.copy("Test file3", new FileWriter(UPLOAD_ROOT + "/test3"));
			imageRepository.save(new Image("test3", thomas));
			
		};

	}

}
