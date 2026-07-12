package com.worknest.config;

import com.worknest.common.storage.FileStorageService;
import com.worknest.common.storage.LocalFileAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class LocalFileStorageConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorageService;
    private final LocalFileAccessInterceptor localFileAccessInterceptor;

    public LocalFileStorageConfig(
            FileStorageService fileStorageService,
            LocalFileAccessInterceptor localFileAccessInterceptor) {
        this.fileStorageService = fileStorageService;
        this.localFileAccessInterceptor = localFileAccessInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations(fileStorageService.getTenantsRootPath().toUri().toString())
                .setCacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .resourceChain(true);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localFileAccessInterceptor)
                .addPathPatterns("/files/**");
    }
}