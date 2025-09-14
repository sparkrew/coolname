package io.github.chains_project.coolname.coverage_checker;

import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new CLIEntryPoint()).execute(args);
        System.exit(exitCode);
    }

    @CommandLine.Command(
            subcommands = {Processor.class},
            mixinStandardHelpOptions = true,
            version = "0.1"
    )
    public static class CLIEntryPoint implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @CommandLine.Command(
            name = "check",
            mixinStandardHelpOptions = true,
            version = "0.1",
            description = "Check which third-party APIs are covered by tests using JaCoCo reports"
    )
    private static class Processor implements Runnable {
        @CommandLine.Option(
                names = {"-t", "--third-party-apis"},
                paramLabel = "THIRD-PARTY-APIS",
                description = "Path to the JSON file containing third-party API usage information",
                required = true
        )
        Path thirdPartyApis;

        @CommandLine.Option(
                names = {"-o", "--output-file"},
                paramLabel = "OUTPUT-FILE",
                description = "Path to the JSON file where analysis results should be written. "
                        + "Defaults to coverage_report.json",
                defaultValue = "coverage_report.json"
        )
        Path reportFile;

        @CommandLine.Option(
                names = {"-j", "--jacoco-files"},
                paramLabel = "JACOCO-FILES",
                description = "One or more JaCoCo XML report files",
                required = true,
                arity = "1..*"
        )
        List<Path> jacocoFiles;

        @Override
        public void run() {
            try {
                DependencyCoverageChecker.check(
                        thirdPartyApis,
                        reportFile,
                        jacocoFiles.stream().map(Path::toFile).toList()
                );
            } catch (Exception e) {
                throw new RuntimeException("Error while checking coverage", e);
            }
        }
    }
}
