package com.hrishabh.codeexecutionengine.service.filehandlingservice.java;

import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO;
import com.hrishabh.codeexecutionengine.dto.CodeSubmissionDTO.QuestionMetadata;
import com.hrishabh.codeexecutionengine.service.filehandlingservice.FileGenerator;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component("javaFileGenerator")
public class JavaFileGenerator implements FileGenerator {

    @Override
    public void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException {
        System.out.println("LOGGING: Starting file generation...");

        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata == null || metadata.getFullyQualifiedPackageName() == null) {
            throw new IllegalArgumentException("QuestionMetadata or fully qualified package name is missing.");
        }

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);

        System.out.println("LOGGING: Generating Main.java content...");
        String mainClassContent = JavaMainClassGenerator.generateMainClassContent(submissionDto);
        Path mainFilePath = packageDir.resolve("Main.java");
        Files.writeString(mainFilePath, mainClassContent);
        System.out.println("LOGGING: Main.java generated at " + mainFilePath.toAbsolutePath());

        System.out.println("LOGGING: Generating Solution.java content...");
        String solutionClassContent = JavaSolutionClassGenerator.generateSolutionClassContent(submissionDto);
        Path solutionFilePath = packageDir.resolve("Solution.java");
        Files.writeString(solutionFilePath, solutionClassContent);
        System.out.println("LOGGING: Solution.java generated at " + solutionFilePath.toAbsolutePath());
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