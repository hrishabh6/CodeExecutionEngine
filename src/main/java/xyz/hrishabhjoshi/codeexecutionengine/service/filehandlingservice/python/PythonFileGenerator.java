package xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.python;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import xyz.hrishabhjoshi.codeexecutionengine.dto.CodeSubmissionDTO;
import xyz.hrishabhjoshi.codeexecutionengine.service.filehandlingservice.FileGenerator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component("pythonFileGenerator")
public class PythonFileGenerator implements FileGenerator {

    @Autowired
    private PythonMainContentGenerator mainContentGenerator;

    @Autowired
    private PythonSolutionContentGenerator solutionContentGenerator;

    @Override
    public void generateFiles(CodeSubmissionDTO submissionDto, Path rootPath) throws IOException {
        System.out.println("LOGGING: Starting Python file generation...");

        CodeSubmissionDTO.QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        if (metadata == null || metadata.getFullyQualifiedPackageName() == null) {
            throw new IllegalArgumentException("QuestionMetadata or fully qualified package name is missing.");
        }

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);

        System.out.println("LOGGING: Generating main.py content...");
        String mainFileContent = mainContentGenerator.generateMainContent(submissionDto);
        Path mainFilePath = packageDir.resolve("main.py");
        Files.writeString(mainFilePath, mainFileContent);
        System.out.println("LOGGING: main.py generated at " + mainFilePath.toAbsolutePath());

        System.out.println("LOGGING: Generating solution.py content...");
        String solutionFileContent = solutionContentGenerator.generateSolutionContent(submissionDto);
        Path solutionFilePath = packageDir.resolve("solution.py");
        Files.writeString(solutionFilePath, solutionFileContent);
        System.out.println("LOGGING: solution.py generated at " + solutionFilePath.toAbsolutePath());
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