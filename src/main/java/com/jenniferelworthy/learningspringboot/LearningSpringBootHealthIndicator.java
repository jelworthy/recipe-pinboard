package com.jenniferelworthy.learningspringboot;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;

@Component
public class LearningSpringBootHealthIndicator implements HealthIndicator {
	
	@Override
	public Health health() {
		try {
		int responseCode = ((HttpURLConnection)
				new URL("http://greglturnquist.com/learning-spring-boot").openConnection()).getResponseCode();
		if (responseCode >= 200 && responseCode < 300) {
			return Health.up().build();
		}
		else {
			return Health.down().withDetail("HTTP status code", responseCode).build();
		}
		}
		catch (IOException e) {
			return Health.down(e).build();
		}
	}

}
