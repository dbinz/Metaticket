package meta.metasearch

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MetasearchApplication

fun main(args: Array<String>) {
	runApplication<MetasearchApplication>(*args)
}
