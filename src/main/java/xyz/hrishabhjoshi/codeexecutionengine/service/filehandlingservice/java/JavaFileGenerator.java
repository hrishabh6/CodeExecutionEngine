package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.java;

import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.FileGenerator;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Component("javaFileGenerator")
public class JavaFileGenerator implements FileGenerator {

    @Override
    public void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException {
        log.debug("Starting file generation...");

        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata == null || metadata.getFullyQualifiedPackageName() == null) {
            throw new IllegalArgumentException("QuestionMetadata or fully qualified package name is missing.");
        }

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);

        log.debug("Generating Main.java content...");
        String mainClassContent = JavaMainClassGenerator.generateMainClassContent(submissionDto);
        Path mainFilePath = packageDir.resolve("Main.java");
        Files.writeString(mainFilePath, mainClassContent);
        log.debug("Main.java generated at {}", mainFilePath.toAbsolutePath());

        log.debug("Generating Solution.java content...");
        String solutionClassContent = JavaSolutionClassGenerator.generateSolutionClassContent(submissionDto);
        Path solutionFilePath = packageDir.resolve("Solution.java");
        Files.writeString(solutionFilePath, solutionClassContent);
        log.debug("Solution.java generated at {}", solutionFilePath.toAbsolutePath());
    }

    private Path createPackageDirectories(Path rootPath, String fullyQualifiedPackageName) {
        Path packagePath = rootPath;
        String[] packageParts = fullyQualifiedPackageName.split("\\.");
        for (String part : packageParts) {
            packagePath = packagePath.resolve(part);
        }
        return packagePath;
    }
}