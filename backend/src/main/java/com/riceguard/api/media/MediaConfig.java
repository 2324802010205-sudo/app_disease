package com.riceguard.api.media;

import com.riceguard.api.config.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class MediaConfig implements WebMvcConfigurer {
    private final AppProperties props;
    private MediaStorage storage;

    public MediaConfig(AppProperties props) {
        this.props = props;
    }

    @Bean
    MediaStorage mediaStorage() {
        AppProperties.Media m = props.media();
        storage = "local".equalsIgnoreCase(m.provider())
                ? new LocalMediaStorage(m.localDir(), props.publicBaseUrl())
                : new CloudinaryMediaStorage(m.cloudinaryUrl());
        return storage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        if ("local".equalsIgnoreCase(props.media().provider())) {
            Path root = Path.of(props.media().localDir()).toAbsolutePath().normalize();
            registry.addResourceHandler("/files/**").addResourceLocations(root.toUri().toString());
        }
    }
}
