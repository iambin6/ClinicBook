package com.nano.clinicbooking.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class ImageWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Lấy folder FE/public/uploads
        String uploadPath = Paths.get(
                        System.getProperty("user.dir"))        // backend folder
                .getParent()                           // project root
                .resolve("frontend")
                .resolve("public")
                .resolve("uploads")
                .toAbsolutePath()
                .toString();

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
