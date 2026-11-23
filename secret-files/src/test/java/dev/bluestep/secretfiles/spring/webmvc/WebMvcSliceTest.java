package dev.bluestep.secretfiles.spring.webmvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.bluestep.secretfiles.spring.ConfigTreeSecretsReloader;
import dev.bluestep.secretfiles.spring.RotatingSecret;
import dev.bluestep.secretfiles.spring.RotatingSecrets;
import dev.bluestep.secretfiles.spring.SecretsReloadedListenerFactory;

/**
 * A {@code @WebMvcTest} slice whose controller draws on {@link RotatingSecrets} starts with no help
 * from the consumer: this artifact lists its auto-configuration under the slice's own imports file
 * ({@code META-INF/spring/org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureWebMvc.imports}).
 * Without that file the slice has no {@code RotatingSecrets} bean and the context fails to load.
 */
@WebMvcTest(properties = "slice.api-key=slice-key-value")
@DisplayName("@WebMvcTest slices include the secret-files auto-configuration")
class WebMvcSliceTest {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ApplicationContext context;

	@Test
	@DisplayName("a controller injecting RotatingSecrets starts and serves")
	void controllerUsingRotatingSecretsServes() throws Exception {
		mvc.perform(get("/key-length")).andExpect(status().isOk()).andExpect(content().string("15"));
	}

	@Test
	@DisplayName("the whole auto-configuration is present, not just the registry")
	void autoConfigurationIsPresent() {
		assertThat(context.getBeanNamesForType(RotatingSecrets.class)).hasSize(1);
		assertThat(context.getBeanNamesForType(ConfigTreeSecretsReloader.class)).hasSize(1);
		assertThat(context.getBeanNamesForType(SecretsReloadedListenerFactory.class)).hasSize(1);
	}

	/** The slice's configuration: the controller only; everything else comes from the slice. */
	@SpringBootConfiguration
	@Import(KeyController.class)
	static class SliceApplication {
	}

	/** Draws a secret from the registry, as a consumer's filter or controller does. */
	@RestController
	static class KeyController {

		private final RotatingSecret key;

		KeyController(final RotatingSecrets secrets) {
			this.key = secrets.required("slice.api-key");
		}

		@GetMapping("/key-length")
		String keyLength() {
			return String.valueOf(key.current().length());
		}
	}
}
