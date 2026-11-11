package meta.metasearch.config

import meta.metasearch.interceptor.LoggingInterceptor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.EnableWebMvc
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableWebMvc
class WebConfig(@Autowired val loggingInterceptor: LoggingInterceptor) : WebMvcConfigurer {

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**") // Specify the path pattern to which this CORS configuration applies
                    .allowedOrigins("*") // Specify the allowed origins
                    .allowedMethods("GET", "POST", "PUT", "DELETE") // Specify the allowed HTTP methods
            }
        }
    }
    @Bean
    fun addInterceptors(): WebMvcConfigurer  {
        return object : WebMvcConfigurer {
            override fun addInterceptors(registry: InterceptorRegistry) {
                registry.addInterceptor(loggingInterceptor)
            }
        }
    }
}
