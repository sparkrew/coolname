package io.github.chains_project.coolname.coverage_checker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.*;

public class DependencyCoverageChecker {

    static final Logger log = LoggerFactory.getLogger(DependencyCoverageChecker.class);

    /**
     * Compares third-party API usage with JaCoCo HTML reports and generates a coverage report.
     *
     * @param thirdPartyApis Path to the JSON file containing third-party API usage information.
     * @param outputFile     Path to the output JSON file where the coverage report will be written.
     * @param jacocoHtmlDirs List of JaCoCo HTML report directories (site/jacoco roots) to be analyzed.
     * @throws Exception if an error occurs during processing.
     */
    public static void check(Path thirdPartyApis, Path outputFile, List<File> jacocoHtmlDirs) throws Exception {

        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootJson = mapper.readTree(thirdPartyApis.toFile());

        Set<String> thirdPartyMethods = new HashSet<>();
        Map<String, String> entryPointMap = new HashMap<>();
        for (JsonNode entry : rootJson.get("thirdPartyPaths")) {
            String tpMethod = entry.get("thirdPartyMethod").asText();
            thirdPartyMethods.add(tpMethod);
            entryPointMap.put(tpMethod, entry.get("entryPoint").asText());
        }

        Set<String> coveredMethods = new HashSet<>();

        for (JsonNode entry : rootJson.get("thirdPartyPaths")) {
            String entryPoint = entry.get("entryPoint").asText();
            String thirdPartyMethod = entry.get("thirdPartyMethod").asText();

            /* Jacoco creates reports under target/site folder. As we are dealing with third party APIs, it is not
            straightforward to get the coverage data from the XML report or the csv file. Instead, we parse the HTML
            report. Each HTML report corresponds to a Java source file.
            The HTML file contains line-by-line coverage information. But it is in HTML format. So we have to parse it
            carefully.*/
            String className = entryPoint.substring(0, entryPoint.lastIndexOf('.'));
            String simpleClassName = className.substring(className.lastIndexOf('.') + 1);
            String packageName = className.substring(0, className.lastIndexOf('.'));
            for (File dir : jacocoHtmlDirs) {
                File htmlFile = dir.toPath()
                        .resolve(packageName)
                        .resolve(simpleClassName + ".java.html")
                        .toFile();
                if (htmlFile.exists()) {
                    if (isThirdPartyInvocationCovered(htmlFile, thirdPartyMethod)) {
                        coveredMethods.add(thirdPartyMethod);
                    }
                }
            }
        }

        // Cross-check
        Set<String> coveredTP = new HashSet<>(thirdPartyMethods);
        coveredTP.retainAll(coveredMethods);

        Set<String> notCoveredTP = new HashSet<>(thirdPartyMethods);
        notCoveredTP.removeAll(coveredMethods);

        // Build JSON output
        ObjectNode report = mapper.createObjectNode();
        report.put("total", thirdPartyMethods.size());
        report.put("covered", coveredTP.size());
        report.put("notCovered", notCoveredTP.size());
        double percent = thirdPartyMethods.isEmpty() ? 0.0 :
                (100.0 * coveredTP.size() / thirdPartyMethods.size());
        report.put("coveragePercent", percent);

        ArrayNode coveredArr = report.putArray("coveredMethods");
        coveredTP.forEach(m -> {
            ObjectNode node = coveredArr.addObject();
            node.put("method", m);
            node.put("entryPoint", entryPointMap.get(m));
        });

        ArrayNode notCoveredArr = report.putArray("notCoveredMethods");
        notCoveredTP.forEach(m -> {
            ObjectNode node = notCoveredArr.addObject();
            node.put("method", m);
            node.put("entryPoint", entryPointMap.get(m));
        });

        try (FileWriter fw = new FileWriter(outputFile.toFile())) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(fw, report);
        }

        log.info("Coverage report written to {}", outputFile);
    }

    private static boolean isThirdPartyInvocationCovered(File htmlFile, String thirdPartyMethod) throws Exception {
        Document doc = Jsoup.parse(htmlFile);
        Elements spans = doc.select("span[id^=L]");

        String className = thirdPartyMethod.substring(0, thirdPartyMethod.lastIndexOf('.'));
        String shortClassName = className.substring(className.lastIndexOf('.') + 1);
        String methodName = thirdPartyMethod.substring(thirdPartyMethod.lastIndexOf('.') + 1);

        /* We parse the HTML file using Jsoup. The way to identify covered method is to check the span class.
         * It is either "fc" (fully covered) or "fc bfc" (partially covered), or "nc" (not covered).
         * Then, we also have to consider <init> (constructors) and <clinit> (static initializers). They won't appear
         * with <init> or <clinit> in the class html file. */
        for (Element span : spans) {
            String codeLine = span.text();
            String clazz = span.className();

            // For Constructor <init>
            if ("<init>".equals(methodName)) {
                if (codeLine.contains("new " + shortClassName + "(") && clazz.contains("fc")) {
                    return true;
                }
            }
            // For Static initializer <clinit>
            else if ("<clinit>".equals(methodName)) {
                // Look for any covered line mentioning the class name
                if (codeLine.contains(shortClassName) && clazz.contains("fc")) {
                    return true;
                }
            }
            // For Normal method
            else {
                if (codeLine.contains(methodName) && clazz.contains("fc")) {
                    return true;
                }
            }
        }
        return false;
    }
}
