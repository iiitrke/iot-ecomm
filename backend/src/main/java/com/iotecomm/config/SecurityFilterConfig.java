package com.iotecomm.config;

import com.iotecomm.security.AdminAuthFilter;
import com.iotecomm.security.DeviceAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecurityFilterConfig {

    @Bean
    public FilterRegistrationBean<DeviceAuthFilter> deviceAuthFilter(IotEcommProperties props) {
        var reg = new FilterRegistrationBean<>(new DeviceAuthFilter(props.devices()));
        reg.addUrlPatterns("/api/device/*");
        reg.setOrder(1);
        return reg;
    }

    @Bean
    public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter(IotEcommProperties props) {
        var reg = new FilterRegistrationBean<>(new AdminAuthFilter(props.adminApiKey()));
        reg.addUrlPatterns("/api/admin/*");
        reg.setOrder(2);
        return reg;
    }
}
