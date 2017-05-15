package com.epam.petclinic.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.compile.JavaCompile

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The QualityAwareJavaPlugin adds configuration that is common to all Java projects.
 * Add java, checkstyle, pmd, findbug plugins.
 *
 * Date: 5/4/2017
 *
 * @author Stanislau Halauniou
 */
public class QualityAwareJavaPlugin implements Plugin<Project> {

    private static final String CODE_QUALITY_DIR = 'code-quality'
    private static final String JAVA_PLUGIN_ID = "java"
    private static final String CHECKSTYLE_PLUGIN_ID = "checkstyle"
    private static final String PMD_PLUGIN_ID = "pmd"
    private static final String FINDBUGS_PLUGIN_ID = "findbugs"

    @Override
    void apply(Project project) {
        final String JAVA_VERSION = "1.8"
        project.plugins.apply(JAVA_PLUGIN_ID)

        project.tasks.withType(JavaCompile) {
            sourceCompatibility = JAVA_VERSION
            targetCompatibility = JAVA_VERSION
            options.encoding = 'UTF-8'
            options.compilerArgs = [
                    '-Xlint:deprecation',
                    '-Xlint:finally',
                    '-Xlint:overrides',
                    '-Xlint:path',
                    '-Xlint:processing',
                    '-Xlint:rawtypes',
                    '-Xlint:varargs',
                    '-Xlint:unchecked'
            ]
        }

        configureCheckStyle(project)
        configurePMD(project)
        configureFindBugs(project)
    }

    private void configureCheckStyle(Project project) {
        project.plugins.apply(CHECKSTYLE_PLUGIN_ID)

        project.checkstyle {
            toolVersion = "7.1"
            config = getToolResource(project, "checkstyle/checkstyle-rules.xml")
            configProperties.suppressionsFile = getToolPath(project, "checkstyle/checkstyle-suppressions.xml")
            ignoreFailures = true
        }

        project.tasks.withType(Checkstyle) {
            reports {
                html.stylesheet(getToolResource(project, 'checkstyle/checkstyle-noframes-severity-sorted.xsl'))
            }
        }
    }

    private void configurePMD(Project project) {
        project.plugins.apply(PMD_PLUGIN_ID)

        project.pmd {
            ignoreFailures = true
            toolVersion = "5.5.1"
            ruleSetFiles = project.rootProject.files("/${CODE_QUALITY_DIR}/pmd/pmd-rules-general.xml",
                    "/${CODE_QUALITY_DIR}/pmd/pmd-rules-prod.xml"
            )
        }

        /*
         * We can disable auto-generated HTML because it will be overridden by ANT task
         */
        project.tasks.withType(Pmd) {
            reports {
                html.enabled(false)
            }
        }

        /*
         * PMD Gradle plugin does not have "html.stylesheet" option, so apply stylesheet through ANT XSLT task
         */
        applyXsltAntTransformation(project, PMD_PLUGIN_ID, 'pmd/pmd-nicerhtml.xsl')
    }

    private void configureFindBugs(Project project) {
        project.plugins.apply(FINDBUGS_PLUGIN_ID)

        project.findbugs {
            toolVersion = "3.0.1"
            sourceSets = [project.sourceSets.main, project.sourceSets.test]
            excludeFilter = project.file("${project.rootDir}/${CODE_QUALITY_DIR}/findbugs/findbugs-exclude.xml")
            ignoreFailures = true
        }

        /*
         * FindBugs plugin does not generate XML and HTML at the same time, so HTML will be generated by ANT
         */
        project.tasks.withType(FindBugs) {
            reports {
                xml.enabled(true)
                xml.withMessages(true)
                html.enabled(false)
            }
        }

        applyXsltAntTransformation(project, FINDBUGS_PLUGIN_ID, 'findbugs/default.xsl')
    }

    private static void applyXsltAntTransformation(Project project, String toolName, String stylesheetRelativePath) {
        project.sourceSets.each { sourceSet ->
            String sourceSetName = sourceSet.name

            Path xmlReportPath = Paths.get("${project[toolName].reportsDir}", "${sourceSetName}.xml")
            Path htmlReportPath = Paths.get("${project[toolName].reportsDir}", "${sourceSetName}.html")

            project.tasks["${toolName}${sourceSetName.capitalize()}"].doLast {
                if (Files.exists(xmlReportPath)) {
                    project.ant.xslt(
                            in: "$xmlReportPath",
                            out: "$htmlReportPath",
                            style: getToolPath(project, stylesheetRelativePath),
                            destdir: "${project[toolName].reportsDir}"
                    )
                }
            }
        }
    }

    private static TextResource getToolResource(Project project, String relativeReference) {
        return project.rootProject.resources.text.fromFile(
                getToolPath(project, relativeReference)
        )
    }

    private static String getToolPath(Project project, String relativeReference) {
        return "${project.rootDir}/${CODE_QUALITY_DIR}/${relativeReference}"
    }

}
