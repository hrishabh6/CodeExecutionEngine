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
        log.info("[JavaFileGen] === START generating Java files ===");
        log.info("[JavaFileGen] submissionRoot={}", rootPath);

        QuestionMetadata metadata = submissionDto.getQuestionMetadata();
        // Note: ExecutionWorkerService ensures metadata is never null and always has a
        // valid package name
        if (metadata == null) {
            log.error("[JavaFileGen] QuestionMetadata is NULL -- this should never happen!");
            throw new IllegalArgumentException("QuestionMetadata is missing (this should never happen)");
        }

        log.info(
                "[JavaFileGen] metadata: functionName={}, returnType={}, packageName={}, params={}, customDS={}, mutationTarget={}",
                metadata.getFunctionName(), metadata.getReturnType(), metadata.getFullyQualifiedPackageName(),
                metadata.getParameters() != null ? metadata.getParameters().size() : 0,
                metadata.getCustomDataStructureNames(), metadata.getMutationTarget());

        Path packageDir = createPackageDirectories(rootPath, metadata.getFullyQualifiedPackageName());
        Files.createDirectories(packageDir);
        log.info("[JavaFileGen] Package dir created: {}", packageDir);

        log.info("[JavaFileGen] Generating Main.java...");
        String mainClassContent = JavaMainClassGenerator.generateMainClassContent(submissionDto);
        Path mainFilePath = packageDir.resolve("Main.java");
        Files.writeString(mainFilePath, mainClassContent);
        log.info("[JavaFileGen] Main.java written to {} (length={})", mainFilePath.toAbsolutePath(),
                mainClassContent.length());

        // [DEBUG_TRACE] Log Main.java content
        log.info(">>> [DEBUG_TRACE] Main.java CONTENT START:\n{}\n>>> [DEBUG_TRACE] Main.java CONTENT END",
                mainClassContent);

        log.info("[JavaFileGen] Generating Solution/Class file...");
        String solutionClassContent = JavaSolutionClassGenerator.generateSolutionClassContent(submissionDto);

        // For design-class questions, filename must match the user's class name, not
        // "Solution"
        String solutionFileName;
        if ("DESIGN_CLASS".equals(metadata.getQuestionType())) {
            solutionFileName = metadata.getFunctionName() + ".java";
            log.info("[JavaFileGen] DESIGN_CLASS mode: using class name '{}' as filename", metadata.getFunctionName());
        } else {
            solutionFileName = "Solution.java";
        }

        Path solutionFilePath = packageDir.resolve(solutionFileName);
        Files.writeString(solutionFilePath, solutionClassContent);
        log.info("[JavaFileGen] {} written to {} (length={})", solutionFileName, solutionFilePath.toAbsolutePath(),
                solutionClassContent.length());

        // [DEBUG_TRACE] Log Solution.java content
        log.info(">>> [DEBUG_TRACE] {} CONTENT START:\n{}\n>>> [DEBUG_TRACE] {} CONTENT END",
                solutionFileName, solutionClassContent, solutionFileName);
        log.info("[JavaFileGen] === END generating Java files ===");
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