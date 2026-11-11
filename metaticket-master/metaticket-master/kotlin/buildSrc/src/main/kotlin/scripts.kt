import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.uppercaseFirstChar
import org.gradle.tooling.BuildException

/**
 *
 * @author dongcatchay
 */
object Script {

    val org.gradle.api.Project.sourceSets: org.gradle.api.tasks.SourceSetContainer
        get() = this.extensions.getByName("sourceSets") as org.gradle.api.tasks.SourceSetContainer

    fun Project.creatingArtifactsConf(name: String): NamedDomainObjectContainerCreatingDelegateProvider<Configuration> {
        return configurations.creating {
            isCanBeConsumed = true
            isCanBeResolved = false

            extendsFrom(configurations["${name}Implementation"])
        }
    }

    fun Project.gettingArtifactsConf(name: String): NamedDomainObjectCollectionDelegateProvider<Configuration> {
        return configurations.getting {
            isCanBeConsumed = true
            isCanBeResolved = false

            extendsFrom(configurations["${name}Implementation"])
        }
    }

    fun Project.registerJar(name: String): TaskProvider<Jar> = tasks.register<Jar>("${name}Jar") {
        from(sourceSets[name].output)

        val project: String = projectDir.name

        dependsOn("compile${name.uppercaseFirstChar()}Kotlin") // Specify the task(s) to depend on

        archiveBaseName.set("$project-$name")
        destinationDirectory.set(file("build/libs"))
    }

    fun Project.exportSourceSet(sourceSet: String) {
        registerJar(sourceSet)

        artifacts {
            add(sourceSet, tasks.getByName("${sourceSet}Jar"))
        }

        tasks.getByName("build") {
            dependsOn("${sourceSet}Jar")
        }
    }
}
